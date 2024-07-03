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

import buckelieg.jdbc.fn.TryBiFunction;
import buckelieg.jdbc.fn.TryConsumer;
import buckelieg.jdbc.fn.TrySupplier;
import buckelieg.jdbc.fn.TryTriConsumer;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static buckelieg.jdbc.Utils.*;
import static java.lang.Math.max;
import static java.sql.ResultSet.FETCH_FORWARD;

@NotThreadSafe
@ParametersAreNonnullByDefault
class SelectQuery extends AbstractQuery<Select, Statement> implements Select {

  protected final Map<String, RSMeta.Column> metaCache;

  protected AtomicInteger currentResultSetNumber = new AtomicInteger();

  volatile ResultSet resultSet;

  volatile ValueReader wrapper;

  int fetchSize = 15;

  private int maxRowsInt = -1;

  private long maxRowsLong = -1L;

  private final Map<String, String> columnNamesMappings = new HashMap<>();

  protected volatile Metadata meta;

  SelectQuery(
		  Map<String, RSMeta.Column> metaCache,
		  TrySupplier<Connection, SQLException> connectionSupplier,
		  TryConsumer<Connection, ? extends Throwable> connectionConsumer,
		  Supplier<ExecutorService> executorServiceSupplier,
		  String query, Object... params) {
	super(connectionSupplier, connectionConsumer, executorServiceSupplier, query, params);
	this.metaCache = metaCache;
  }

  @Nonnull
  @Override
  public final <T> ForBatch<T> forBatch(TryBiFunction<ValueReader, Integer, T, SQLException> mapper) {
	if (null == mapper) throw new NullPointerException("Mapper must be provided");
	return new Select.ForBatch<T>() { // TODO keep ordering?

	  int batchSize = fetchSize;

	  @Nonnull
	  @Override
	  public ForBatch<T> size(int batchSize) {
		this.batchSize = max(1, batchSize);
		return this;
	  }

	  @SuppressWarnings("unchecked")
	  @Nonnull
	  @Override
	  public Stream<T> execute(TryTriConsumer<List<T>, Session, Integer, ? extends Exception> batchProcessor) {
		if (null == batchProcessor) throw new NullPointerException("Batch processor must be provided");
		final BatchSpliterator<T> splIterator = new BatchSpliterator<>(SelectQuery.this, mapper, batchProcessor, batchSize <= 0 ? fetchSize : batchSize);
		return (Stream<T>) proxy(StreamSupport.stream(splIterator, false).onClose(splIterator::close));
	  }
	};
  }

  @Nonnull
  @Override
  public <T> T forMeta(Function<Metadata, T> mapper) {
	if (null == mapper) throw new NullPointerException("Mapper must be provided");
	try {
	  statement = prepareStatement();
	  resultSet = doExecute(statement);
	  return mapper.apply(new RSMeta(getConnection()::getMetaData, resultSet::getMetaData, metaCache));
	} catch (SQLException e) {
	  throw newSQLRuntimeException(e);
	} finally {
	  close();
	}
  }

  @SuppressWarnings("unchecked")
  @Nonnull
  @Override
  public final <T> Stream<T> execute(TryBiFunction<ValueReader, Integer, T, SQLException> mapper) {
	if (null == mapper) throw new NullPointerException("Mapper must be provided");
	return (Stream<T>) proxy(StreamSupport.stream(new SequentialSpliterator<>(SelectQuery.this, mapper), false).onClose(this::close));
  }

  protected ResultSet doExecute(Statement statement) throws SQLException {
	configureStatement(statement);
	return isPrepared ? ((PreparedStatement) statement).executeQuery() : statement.execute(query) ? statement.getResultSet() : null;
  }

  @Nonnull
  @Override
  public final Select fetchSize(int size) {
	this.fetchSize = max(1, size);
	return this;
  }

  @Nonnull
  @Override
  public final Select maxRows(int max) {
	this.maxRowsInt = max(0, max);
	this.maxRowsLong = -1L;
	return this;
  }

  @Nonnull
  @Override
  public final Select maxRows(long max) {
	this.maxRowsLong = max(0, max);
	this.maxRowsInt = -1;
	return this;
  }

  protected Statement prepareStatement() throws SQLException {
	return isPrepared
			? setStatementParameters(getConnection().prepareStatement(query), params)
			: getConnection().createStatement();
  }

  private String getColumnName(String columnName, Metadata meta) {
	return columnNamesMappings.computeIfAbsent(columnName, name -> meta.names().stream().filter(c -> c.equalsIgnoreCase(name)).findFirst().orElse(name));
  }

  private String getColumnName(String columnName, Map<String, Object> row) {
	return columnNamesMappings.computeIfAbsent(columnName, name -> row.keySet().stream().filter(Objects::nonNull).filter(c -> c.equalsIgnoreCase(name)).findFirst().orElse(name));
  }

  protected final void configureStatement(Statement statement) throws SQLException {
	setQueryBasicParameters(statement);
	if (fetchSize > 0) {
	  accept(() -> statement.setFetchSize(fetchSize)); // 0 value is ignored by Statement.setFetchSize;
	  accept(() -> statement.setFetchDirection(FETCH_FORWARD));
	}
	if (maxRowsInt != -1) accept(() -> statement.setMaxRows(maxRowsInt));
	if (maxRowsLong != -1L) accept(() -> statement.setLargeMaxRows(maxRowsLong));
  }

}
