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

import buckelieg.fn.TryBiFunction;
import buckelieg.fn.TryConsumer;
import buckelieg.fn.TrySupplier;
import buckelieg.fn.TryTriConsumer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static buckelieg.jdbc.Utils.newSQLRuntimeException;
import static buckelieg.jdbc.Utils.proxy;
import static buckelieg.jdbc.Utils.setStatementParameters;
import static java.lang.Math.max;
import static java.sql.ResultSet.FETCH_FORWARD;

@SuppressWarnings("SqlSourceToSinkFlow")
class SelectQuery extends AbstractQuery<Select, Statement> implements Select {

  protected final Map<String, MetadataImpl.Column> metaCache;

  protected AtomicInteger currentResultSetNumber = new AtomicInteger();

  volatile ResultSet resultSet;

  volatile ValueReader wrapper;

  int fetchSize = 15;

  private int maxRowsInt = -1;

  private long maxRowsLong = -1L;

  protected volatile Metadata meta;

  SelectQuery(
		  Map<String, MetadataImpl.Column> metaCache,
		  TrySupplier<Connection, SQLException> connectionSupplier,
		  TryConsumer<Connection, ? extends Throwable> connectionConsumer,
		  ExecutorService executorService,
		  String query, Object... params) {
	super(connectionSupplier, connectionConsumer, executorService, query, params);
	this.metaCache = metaCache;
  }

  @Override
  public final <T> ForBatch<T> forBatch(TryBiFunction<ValueReader, Integer, T, SQLException> mapper) {
	if (null == mapper) throw new NullPointerException("Mapper must be provided");
	return new Select.ForBatch<T>() { // TODO keep ordering?

	  int batchSize = fetchSize;

	  @Override
	  public ForBatch<T> size(int batchSize) {
		this.batchSize = max(1, batchSize);
		return this;
	  }

	  @SuppressWarnings("unchecked")
	  @Override
	  public Stream<T> execute(TryTriConsumer<List<T>, Session, Integer, ? extends Exception> batchProcessor) {
		if (null == batchProcessor) throw new NullPointerException("Batch processor must be provided");
		final BatchSpliterator<T> splIterator = new BatchSpliterator<>(SelectQuery.this, mapper, batchProcessor, batchSize <= 0 ? fetchSize : batchSize);
		return (Stream<T>) proxy(StreamSupport.stream(splIterator, false).onClose(splIterator::close));
	  }
	};
  }

  @Override
  public <T> T forMeta(Function<Metadata, T> mapper) {
	if (null == mapper) throw new NullPointerException("Mapper must be provided");
	TrySupplier<ResultSetMetaData, SQLException> metadataSupplier;
	try {
	  statement = prepareStatement();
	  if (!isPrepared) {
		resultSet = doExecute(statement);
		metadataSupplier = null == resultSet ? null : resultSet::getMetaData;
	  } else metadataSupplier = ((PreparedStatement) statement)::getMetaData;
	  return mapper.apply(new MetadataImpl(getConnection()::getMetaData, metadataSupplier, metaCache));
	} catch (SQLException e) {
	  throw newSQLRuntimeException(e);
	} finally {
	  close();
	}
  }

  @SuppressWarnings("unchecked")
  @Override
  public final <T> Stream<T> execute(TryBiFunction<ValueReader, Integer, T, SQLException> mapper) {
	if (null == mapper) throw new NullPointerException("Mapper must be provided");
	return (Stream<T>) proxy(StreamSupport.stream(new SequentialSpliterator<>(SelectQuery.this, mapper), false).onClose(this::close));
  }

  protected ResultSet doExecute(Statement statement) throws SQLException {
	configureStatement(statement);
	return isPrepared ? ((PreparedStatement) statement).executeQuery() : statement.execute(query) ? statement.getResultSet() : null;
  }

  @Override
  public final Select fetchSize(int size) {
	this.fetchSize = max(1, size);
	return this;
  }

  @Override
  public final Select maxRows(int max) {
	this.maxRowsInt = max(0, max);
	this.maxRowsLong = -1L;
	return this;
  }

  @Override
  public final Select maxRows(long max) {
	this.maxRowsLong = max(0, max);
	this.maxRowsInt = -1;
	return this;
  }

  protected Statement prepareStatement() throws SQLException {
	return isPrepared
			? setStatementParameters(getConnection().prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY), params)
			: getConnection().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

  }

  protected final void configureStatement(Statement statement) throws SQLException {
	setQueryBasicParameters(statement);
	if (fetchSize > 0) {
	  accept(() -> {
		statement.setFetchSize(fetchSize); // 0 value is ignored by Statement.setFetchSize;
		statement.setFetchDirection(FETCH_FORWARD);
	  });
	}
	if (maxRowsInt != -1) accept(() -> statement.setMaxRows(maxRowsInt));
	if (maxRowsLong != -1L) accept(() -> statement.setLargeMaxRows(maxRowsLong));
  }

}
