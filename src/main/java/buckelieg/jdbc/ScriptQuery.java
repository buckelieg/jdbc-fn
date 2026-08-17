/*
 * Copyright 2016- Anatoly Kutyakov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package buckelieg.jdbc;

import buckelieg.fn.TryBiConsumer;
import buckelieg.fn.TrySupplier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import static buckelieg.jdbc.Utils.STATEMENT_DELIMITER;
import static buckelieg.jdbc.Utils.isAnonymous;
import static buckelieg.jdbc.Utils.isProcedure;
import static buckelieg.jdbc.Utils.newSQLRuntimeException;
import static buckelieg.jdbc.Utils.prepareQuery;
import static buckelieg.jdbc.Utils.setStatementParameters;
import static java.lang.Math.max;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

final class ScriptQuery<T extends Map.Entry<String, ?>> implements Script {

  private static final Consumer<? super Throwable> NOOP = e -> {};

  private final String script;

  private final TrySupplier<Connection, SQLException> connectionSupplier;

  private final TryBiConsumer<Connection, Boolean, ? extends Throwable> connectionConsumer;

  private final ExecutorService executorService;

  private final List<T> params;
  private final AtomicReference<String> query = new AtomicReference<>();
  private int timeout;
  private TimeUnit unit = TimeUnit.SECONDS;
  private Consumer<String> logger;
  private boolean escaped = true;
  private boolean skipErrors = true;
  private boolean skipWarnings = true;
  private boolean poolable;
  private Connection connectionInUse;

  private final AtomicBoolean rollbackOnly = new AtomicBoolean();

  /**
   * Creates script executor query
   *
   * @param connectionSupplier db connection supplier function
   * @param script             an arbitrary SQL script to execute
   * @throws IllegalArgumentException in case of corrupted script (like illegal comment lines encountered)
   */
  ScriptQuery(
		  TrySupplier<Connection, SQLException> connectionSupplier,
		  TryBiConsumer<Connection, Boolean, ? extends Throwable> connectionConsumer,
		  ExecutorService executorService,
		  String script,
		  Iterable<T> namedParams) {
	this.connectionSupplier = connectionSupplier;
	this.connectionConsumer = connectionConsumer;
	this.executorService = executorService;
	this.script = script;
	this.params = namedParams == null ? emptyList() : StreamSupport.stream(namedParams.spliterator(), false).collect(toList());
  }

  /**
   * Executes script. All comments are cut out.
   * Therefore, all RDBMS-specific hints are ignored (like Oracle's <code>APPEND</code>) etc.
   *
   * @return a time, taken by this script to complete in milliseconds
   * @throws SQLRuntimeException in case of any errors including {@link SQLWarning} (if corresponding option is set) OR (if timeout is set) - in case of execution run out of time.
   */
  @Override
  public Long execute() {
	rollbackOnly.set(false);
	TrySupplier<Long, SQLException> execute = () -> {
	  connectionInUse = connectionSupplier.get();
	  return doExecute(connectionInUse);
	};
	Future<Long> task = null;
	try {
	  if (timeout == 0) return execute.get();
	  else {
		task = executorService.submit(execute::get);
		return task.get(timeout, unit);
	  }
	} catch (SQLException e) {
	  rollbackOnly.set(true);
	  throw newSQLRuntimeException(e);
	} catch (InterruptedException e) {
	  rollbackOnly.set(true);
	  Thread.currentThread().interrupt();
	  throw new RuntimeException(e);
	} catch (ExecutionException | TimeoutException e) {
	  rollbackOnly.set(true);
	  if (null != task) task.cancel(true);
	  throw newSQLRuntimeException(e);
	}
  }

  private long doExecute(Connection connection) throws SQLException {
	long start = currentTimeMillis();
	boolean isWarnings = false;
	boolean successful = false;
	Statement statement = null;
	try {
	  for (String query : script.split(STATEMENT_DELIMITER)) {
		boolean isPrepared = !isAnonymous(query);
		String queryString = query;
		try {
		  if (isPrepared) {
			Map.Entry<String, Object[]> preparedQuery = prepareQuery(query, params);
			statement = isProcedure(preparedQuery.getKey()) ? connection.prepareCall(preparedQuery.getKey()) : connection.prepareStatement(preparedQuery.getKey());
			setStatementParameters((PreparedStatement) statement, preparedQuery.getValue());
			if (logger != null) queryString = Utils.asSQL(preparedQuery.getKey(), preparedQuery.getValue());
		  } else statement = connection.createStatement();
		  try {
			statement.setPoolable(poolable);
		  } catch (AbstractMethodError ame) {
			// ignore
		  }
		  try {
			statement.setEscapeProcessing(escaped);
		  } catch (AbstractMethodError ame) {
			// ignore
		  }
		  if (logger != null) logger.accept(queryString);
		  if (isPrepared) ((PreparedStatement) statement).execute();
		  else statement.execute(query);
		  if (!skipWarnings && statement.getWarnings() != null) {
			isWarnings = true;
			throw statement.getWarnings();
		  }
		} catch (SQLException e) {
		  if (!skipErrors && !isWarnings) throw e;
		  else if (isWarnings && !skipWarnings) throw e;
		} finally {
		  isWarnings = false;
		  if (statement != null) statement.close();
		}
	  }
	  successful = true;
	} finally {
	  close(successful && !rollbackOnly.get());
	}
	return currentTimeMillis() - start;
  }

  @Override
  public Script escaped(boolean escapeProcessing) {
	this.escaped = escapeProcessing;
	return this;
  }

  @Override
  public Script print(Consumer<String> printer) {
	if (null == printer) throw new NullPointerException("Printer must be provided");
	printer.accept(asSQL());
	return this;
  }

  @Override
  public Script skipErrors(boolean skipErrors) {
	this.skipErrors = skipErrors;
	return this;
  }

  @Override
  public Script skipWarnings(boolean skipWarnings) {
	this.skipWarnings = skipWarnings;
	return this;
  }

  @Override
  public Script timeout(int timeout, TimeUnit unit) {
	this.unit = requireNonNull(unit, "Time Unit must be provided");
	this.timeout = max(0, timeout);
	return this;
  }

  @Override
  public String toString() {
	return script;
  }

  @Override
  public Script poolable(boolean poolable) {
	this.poolable = poolable;
	return this;
  }

  @Override
  public Script verbose(Consumer<String> logger) {
	this.logger = requireNonNull(logger, "Logger must be provided");
	return this;
  }

  @Override
  public String asSQL() {
	return query.updateAndGet(sql -> {
	  if (sql == null) {
		Map.Entry<String, Object[]> preparedScript = prepareQuery(script, params);
		sql = Utils.asSQL(preparedScript.getKey(), preparedScript.getValue());
	  }
	  return sql;
	});
  }

  void close(boolean successful) {
	try {
	  if (null != connectionConsumer) connectionConsumer.accept(connectionInUse, successful);
	} catch (Throwable e) {
	  throw newSQLRuntimeException(e);
	} finally {
	  connectionInUse = null;
	}
  }
}
