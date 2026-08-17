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
import buckelieg.fn.TryRunnable;
import buckelieg.fn.TrySupplier;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static buckelieg.jdbc.Utils.newSQLRuntimeException;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("unchecked")
abstract class AbstractQuery<Q extends Query<Q>, S extends Statement> implements Query<Q> {

  protected S statement;
  protected final String query;
  protected final TrySupplier<Connection, SQLException> connectionSupplier;
  private final TryBiConsumer<Connection, Boolean, ? extends Throwable> connectionConsumer;
  protected final ExecutorService executorService;
  protected boolean skipWarnings = true;
  protected final boolean isPrepared;
  protected int timeout;
  protected TimeUnit unit = TimeUnit.SECONDS;
  protected boolean poolable;
  protected boolean escapeProcessing = true;
  protected Runnable finisher = () -> {};

  protected final Object[] params;
  private final AtomicReference<Connection> connectionInUse = new AtomicReference<>();

  private final AtomicBoolean successful = new AtomicBoolean();

  AbstractQuery(
		  TrySupplier<Connection, SQLException> connectionSupplier,
		  TryBiConsumer<Connection, Boolean, ? extends Throwable> connectionConsumer,
		  ExecutorService executorService,
		  String query, Object... params) {
	this.query = query;
	this.connectionSupplier = connectionSupplier;
	this.connectionConsumer = connectionConsumer;
	this.executorService = executorService;
	this.params = params;
	this.isPrepared = params != null && params.length != 0;
  }

  void close() {
	try {
	  if (null != statement && !statement.isClosed()) {
		statement.close(); // by JDBC spec: subsequently closes all result sets opened by this statement
	  }
	} catch (SQLException e) {
	  markFailed();
	  throw newSQLRuntimeException(e);
	} catch (RuntimeException e) {
	  markFailed();
	  throw e;
	} finally {
	  if (null != connectionConsumer) {
		try {
		  connectionConsumer.accept(connectionInUse.getAndSet(null), successful.get());
		} catch (Throwable e) {
		  throw newSQLRuntimeException(e);
		}
	  }
	}
  }

  final void markSuccessful() {
	successful.set(true);
  }

  final void markFailed() {
	successful.set(false);
  }

  final void setQueryBasicParameters(S statement) throws SQLException {
	accept(() -> {
	  statement.setQueryTimeout(max((int) requireNonNull(unit, "Time Unit must be provided").toSeconds(timeout), 0));
	  statement.setPoolable(poolable);
	  statement.setEscapeProcessing(escapeProcessing);
	});
  }

  @Override
  public Q poolable(boolean poolable) {
	this.poolable = poolable;
	return (Q) this;
  }

  @Override
  public Q timeout(int timeout, TimeUnit unit) {
	this.unit = requireNonNull(unit, "Time unit must be provided");
	this.timeout = timeout;
	return (Q) this;
  }

  @Override
  public Q escaped(boolean escapeProcessing) {
	this.escapeProcessing = escapeProcessing;
	return (Q) this;
  }

  @Override
  public Q skipWarnings(boolean skipWarnings) {
	this.skipWarnings = skipWarnings;
	return (Q) this;
  }

  @Override
  public Q print(Consumer<String> printer) {
	if (null == printer)
	  throw new NullPointerException("Printer must be provided");
	printer.accept(asSQL());
	return (Q) this;
  }

  final void accept(TryRunnable<SQLException> action) throws SQLException {
	try {
	  action.run();
	  if (!skipWarnings && statement.getWarnings() != null) throw statement.getWarnings();
	} catch (AbstractMethodError ame) {
	  // silently ignore this possible vendor-specific JDBC driver's error.
	}
  }

  String asSQL(String query, Object... params) {
	return Utils.asSQL(query, params);
  }

  @Override
  public final String asSQL() {
	return asSQL(query, params);
  }

  @Override
  public String toString() {
	return query;
  }

  protected final Connection getConnection() {
	return connectionInUse.updateAndGet(connection -> {
	  if (null == connection) {
		try {
		  markFailed();
		  connection = connectionSupplier.get();
		} catch (SQLException e) {
		  throw Utils.newSQLRuntimeException(e);
		}
	  }
	  return connection;
	});
  }

}
