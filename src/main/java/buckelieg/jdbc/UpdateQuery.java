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

import buckelieg.jdbc.fn.TryConsumer;
import buckelieg.jdbc.fn.TryFunction;
import buckelieg.jdbc.fn.TrySupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static buckelieg.jdbc.Utils.*;
import static java.lang.Math.max;
import static java.sql.Statement.RETURN_GENERATED_KEYS;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.of;

@SuppressWarnings("unchecked")
@NotThreadSafe
@ParametersAreNonnullByDefault
final class UpdateQuery extends AbstractQuery<Update, Statement> implements Update {

  private static final int DEFAULT_BATCH_SIZE = 1;

  private final Object[][] batch;
  private boolean isLarge;
  private int batchSize = DEFAULT_BATCH_SIZE;
  private int[] colIndices = null;
  private String[] colNames = null;

  UpdateQuery(
		  TrySupplier<Connection, SQLException> connectionSupplier,
		  TryConsumer<Connection, ? extends Throwable> connectionConsumer,
		  Supplier<ExecutorService> executorServiceSupplier,
		  String query, Object[]... batch) {
	super(connectionSupplier, connectionConsumer, executorServiceSupplier, query, (Object) batch);
	this.batch = batch;
  }

  @Override
  public Update large(boolean isLarge) {
	this.isLarge = isLarge;
	return this;
  }

  @Override
  public Update batch(int batchSize) {
	this.batchSize = max(DEFAULT_BATCH_SIZE, batchSize);
	return this;
  }

  @Nonnull
  @Override
  public <T> List<T> execute(TryFunction<ValueReader, T, SQLException> generatedValuesMapper) {
	if (null == generatedValuesMapper) throw new NullPointerException("Generated values mapper must be provided");
	try {
	  prepareStatement(true);
	  return (DEFAULT_BATCH_SIZE != batchSize && getConnection().getMetaData().supportsBatchUpdates())
			  ? executeUpdateBatchWithGeneratedKeys(generatedValuesMapper)
			  : executeUpdateWithGeneratedKeys(generatedValuesMapper);
	} catch (SQLException e) {
	  throw newSQLRuntimeException(e);
	} finally {
	  close();
	}
  }

  @Nonnull
  @Override
  public <T> List<T> execute(TryFunction<ValueReader, T, SQLException> generatedValuesMapper, String... colNames) {
	if (null == colNames) throw new NullPointerException("Column names must be provided");
	this.colNames = colNames;
	return execute(generatedValuesMapper);
  }

  @Nonnull
  @Override
  public <T> List<T> execute(TryFunction<ValueReader, T, SQLException> generatedValuesMapper, int... colIndices) {
	if (null == colIndices) throw new NullPointerException("Column indices must be provided");
	this.colIndices = colIndices;
	return execute(generatedValuesMapper);
  }

  @Nonnull
  public Long execute() {
	try {
	  prepareStatement(false);
	  return (DEFAULT_BATCH_SIZE != batchSize && getConnection().getMetaData().supportsBatchUpdates()) ? executeUpdateBatch() : executeUpdate();
	} catch (SQLException e) {
	  throw newSQLRuntimeException(e);
	} finally {
	  close();
	}
  }

  private void prepareStatement(boolean useGeneratedKeys) throws SQLException {
	if (useGeneratedKeys) {
	  if (null != colNames && 0 != colNames.length) statement = getConnection().prepareStatement(query, colNames);
	  else if (null != colIndices && 0 != colIndices.length) statement = getConnection().prepareStatement(query, colIndices);
	  else statement = getConnection().prepareStatement(query, RETURN_GENERATED_KEYS);
	} else statement = isPrepared ? getConnection().prepareStatement(query) : getConnection().createStatement();
	setQueryBasicParameters(statement);
  }

  private <K> List<K> executeUpdateWithGeneratedKeys(TryFunction<ValueReader, K, SQLException> valueMapper) throws SQLException {
	List<K> genKeys = new ArrayList<>();
	for (Object[] params : batch) {
	  if (isLarge) setStatementParameters((PreparedStatement) statement, params).executeLargeUpdate();
	  else setStatementParameters((PreparedStatement) statement, params).executeUpdate();
	  genKeys.addAll(toList(statement.getGeneratedKeys(), valueMapper));
	}
	return genKeys;
  }

  private <K> List<K> executeUpdateBatchWithGeneratedKeys(TryFunction<ValueReader, K, SQLException> valueMapper) throws SQLException {
	return processBatch(longs -> new ArrayList<>(toList(statement.getGeneratedKeys(), valueMapper)));
  }

  private long executeUpdate() throws SQLException {
	long[] longs = new long[batch.length];
	int cursor = 0;
	for (Object[] params : batch) {
	  if (isPrepared) {
		statement = setStatementParameters((PreparedStatement) statement, params);
		longs[cursor] = isLarge ? ((PreparedStatement) statement).executeLargeUpdate() : ((PreparedStatement) statement).executeUpdate();
	  } else longs[cursor] = isLarge ? statement.executeLargeUpdate(query) : statement.executeUpdate(query);
	  cursor++;
	}
	return Arrays.stream(longs).sum();
  }

  private long executeUpdateBatch() throws SQLException {
	return processBatch(longs -> Arrays.stream(longs).sum());
  }

  @Override
  String asSQL(String query, Object... params) {
	return stream(params).flatMap(p -> of((Object[]) p)).map(p -> super.asSQL(query, (Object[]) p)).collect(joining(STATEMENT_DELIMITER));
  }

  private <T> T processBatch(TryFunction<long[], T, SQLException> resultProcessor) throws SQLException {
	List<long[]> results = new ArrayList<>();
	int totalSize = 0;
	int index = 1;
	for (Object[] params : batch) {
	  if (0 != index && 0 == index % batchSize) {
		index = 0;
		totalSize += executeBatch(results);
	  }
	  index++;
	  if (isPrepared) setStatementParameters((PreparedStatement) statement, params).addBatch();
	  else statement.addBatch(query);
	}
	if (index > 0) totalSize += executeBatch(results);
	long[] toProcess = new long[totalSize];
	index = 0;
	for (long[] longs : results) {
	  System.arraycopy(longs, 0, toProcess, index, longs.length);
	  index += longs.length;
	}
	return resultProcessor.apply(toProcess);
  }

  private int executeBatch(List<long[]> acc) throws SQLException {
	long[] longs;
	if (isLarge) longs = statement.executeLargeBatch();
	else {
	  int[] ints = statement.executeBatch();
	  longs = new long[ints.length];
	  for (int i = 0; i < ints.length; i++) longs[i] = ints[i];
	}
	acc.add(longs);
	statement.clearBatch();
	return longs.length;
  }

  private <T> List<T> toList(@Nullable ResultSet resultSet, TryFunction<ValueReader, T, SQLException> mapper) throws SQLException {
	if (null == resultSet)
	  return Collections.emptyList(); // derby (current version - 10.14.2.0) returns null instead of empty resultSet object
	ValueReader valueReader = ValueGetters.reader(new RSMeta(getConnection()::getMetaData, resultSet::getMetaData, new ConcurrentHashMap<>()), resultSet);
	List<T> result = new ArrayList<>();
	while (resultSet.next())
	  result.add(requireNonNull(mapper.apply(valueReader)));
	return result;
  }

}
