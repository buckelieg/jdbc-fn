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
import buckelieg.fn.TryBiFunction;
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
import static java.util.Objects.requireNonNull;

@SuppressWarnings("SqlSourceToSinkFlow")
class SelectQuery extends AbstractQuery<Select, Statement> implements Select {

  protected final Map<String, MetadataImpl.Column> metaCache;

  final TrySupplier<Connection, SQLException> processingConnectionSupplier;

  final TryBiConsumer<Connection, Boolean, ? extends Throwable> processingConnectionCloser;

  protected AtomicInteger currentResultSetNumber = new AtomicInteger();

  volatile ResultSet resultSet;

  volatile ValueReader wrapper;

  int fetchSize = 15;

  private Streaming streaming = Streaming.REQUIRED;

  private int maxRowsInt = -1;

  private long maxRowsLong = -1L;

  protected volatile Metadata meta;

  private volatile StreamingStrategy streamingStrategy;

  SelectQuery(
		  Map<String, MetadataImpl.Column> metaCache,
		  TrySupplier<Connection, SQLException> connectionSupplier,
		  TryBiConsumer<Connection, Boolean, ? extends Throwable> connectionConsumer,
		  ExecutorService executorService,
		  String query, Object... params) {
	this(metaCache, connectionSupplier, connectionConsumer, connectionSupplier, connectionConsumer, executorService, query, params);
  }

  SelectQuery(
		  Map<String, MetadataImpl.Column> metaCache,
		  TrySupplier<Connection, SQLException> connectionSupplier,
		  TryBiConsumer<Connection, Boolean, ? extends Throwable> connectionConsumer,
		  TrySupplier<Connection, SQLException> processingConnectionSupplier,
		  TryBiConsumer<Connection, Boolean, ? extends Throwable> processingConnectionCloser,
		  ExecutorService executorService,
		  String query, Object... params) {
	super(connectionSupplier, connectionConsumer, executorService, query, params);
	this.metaCache = metaCache;
	this.processingConnectionSupplier = processingConnectionSupplier;
	this.processingConnectionCloser = processingConnectionCloser;
  }

  @Override
  public final <T> ForBatch<T> forBatch(TryBiFunction<ValueReader, Integer, T, SQLException> mapper) {
	if (null == mapper) throw new NullPointerException("Mapper must be provided");
	return new Select.ForBatch<T>() { // TODO keep ordering?

	  int batchSize = fetchSize;
	  int concurrency = BatchSpliterator.defaultConcurrency();

	  @Override
	  public ForBatch<T> size(int batchSize) {
		this.batchSize = max(1, batchSize);
		return this;
	  }

	  @Override
	  public ForBatch<T> concurrency(int concurrency) {
		this.concurrency = max(1, concurrency);
		return this;
	  }

	  @SuppressWarnings("unchecked")
	  @Override
	  public Stream<T> execute(TryTriConsumer<List<T>, Session, Integer, ? extends Exception> batchProcessor) {
		if (null == batchProcessor) throw new NullPointerException("Batch processor must be provided");
		final BatchSpliterator<T> splIterator = new BatchSpliterator<>(SelectQuery.this, mapper, batchProcessor, batchSize <= 0 ? fetchSize : batchSize, concurrency);
		return (Stream<T>) proxy(
				StreamSupport.stream(splIterator, false).onClose(splIterator::close),
				SelectQuery.this::markSuccessful,
				SelectQuery.this::markFailed,
				splIterator::unordered
		);
	  }
	};
  }

  @Override
  public <T> T forMeta(Function<Metadata, T> mapper) {
	if (null == mapper) throw new NullPointerException("Mapper must be provided");
	try {
	  statement = prepareStatement();
	  MetadataImpl metadata = preloadMetadata(statement);
	  if (null == metadata) {
		resultSet = doExecute(statement);
		metadata = null == resultSet
				? new MetadataImpl(getConnection()::getMetaData, null, metaCache).snapshot()
				: new MetadataImpl(null, resultSet::getMetaData, metaCache).snapshot();
		if (null != resultSet) resultSet.close();
	  }
	  T result = mapper.apply(metadata);
	  markSuccessful();
	  return result;
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
	return (Stream<T>) proxy(StreamSupport.stream(new SequentialSpliterator<>(SelectQuery.this, mapper), false).onClose(this::close), this::markSuccessful, this::markFailed);
  }

  protected ResultSet doExecute(Statement statement) throws SQLException {
	configureStatement(statement);
	return statement instanceof PreparedStatement
			? ((PreparedStatement) statement).executeQuery()
			: statement.execute(query) ? statement.getResultSet() : null;
  }

  final boolean initializeResultSet() throws SQLException {
	statement = prepareStatement();
	preloadMetadata(statement);
	resultSet = doExecute(statement);
	if (null == resultSet) return false;

	currentResultSetNumber.incrementAndGet();
	meta = new MetadataImpl(null, resultSet::getMetaData, metaCache).snapshot();
	wrapper = ValueGetters.reader(meta, resultSet);
	return true;
  }

  protected MetadataImpl preloadMetadata(Statement statement) throws SQLException {
	if (!(statement instanceof PreparedStatement)) return null;

	ResultSetMetaData resultSetMetaData = ((PreparedStatement) statement).getMetaData();
	MetadataImpl metadata;
	if (null == resultSetMetaData) metadata = probeMetadata((PreparedStatement) statement);
	else {
	  final ResultSetMetaData preliminary = resultSetMetaData;
	  metadata = new MetadataImpl(getConnection()::getMetaData, () -> preliminary, metaCache).snapshot();
	}
	return null == metadata ? null : metadata.preload();
  }

  private MetadataImpl probeMetadata(PreparedStatement statement) throws SQLException {
	configureStatement(statement);
	statement.setMaxRows(1);
	try (ResultSet probe = statement.executeQuery()) {
	  return null == probe ? null : new MetadataImpl(getConnection()::getMetaData, probe::getMetaData, metaCache).snapshot();
	}
  }

  @Override
  public final Select fetchSize(int size) {
	this.fetchSize = max(1, size);
	return this;
  }

  @Override
  public final Select streaming(Streaming streaming) {
	this.streaming = requireNonNull(streaming, "Streaming mode must be provided");
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
	return setStatementParameters(
			getConnection().prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY),
			params
	);

  }

  protected final void configureStatement(Statement statement) throws SQLException {
	setQueryBasicParameters(statement);
	accept(() -> strategy().configure(getConnection(), statement, fetchSize, streaming));
	if (maxRowsInt != -1) accept(() -> statement.setMaxRows(maxRowsInt));
	else if (maxRowsLong != -1L) accept(() -> statement.setLargeMaxRows(maxRowsLong));
	else accept(() -> statement.setMaxRows(0));
  }

  private StreamingStrategy strategy() throws SQLException {
	if (null == streamingStrategy) streamingStrategy = StreamingStrategy.resolve(getConnection());
	return streamingStrategy;
  }

}
