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
package buckelieg.jdbc.fn;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;
import java.sql.*;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static buckelieg.jdbc.fn.Utils.*;
import static java.sql.Statement.RETURN_GENERATED_KEYS;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.of;

@SuppressWarnings("unchecked")
@NotThreadSafe
@ParametersAreNonnullByDefault
final class UpdateQuery extends AbstractQuery<Statement> implements Update {

    private final Object[][] batch;
    private boolean isLarge;
    private boolean isBatch;
    private int[] colIndices = null;
    private String[] colNames = null;
    private boolean useGeneratedKeys = false;

    UpdateQuery(Connection connection, String query, Object[]... batch) {
        super(connection, query, (Object) batch);
        this.batch = batch;
    }

    @Override
    public Update large(boolean isLarge) {
        this.isLarge = isLarge;
        return this;
    }

    @Override
    public Update batch(boolean isBatch) {
        this.isBatch = isBatch;
        return this;
    }

    @Nonnull
    @Override
    public Update poolable(boolean poolable) {
        this.isPoolable = poolable;
        return this;
    }

    @Nonnull
    @Override
    public Update timeout(int timeout, TimeUnit unit) {
        this.timeout = timeout;
        this.unit = unit;
        return this;
    }

    @Nonnull
    @Override
    public Update escaped(boolean escapeProcessing) {
        this.isEscaped = escapeProcessing;
        return this;
    }

    @Nonnull
    @Override
    public Update skipWarnings(boolean skipWarnings) {
        this.skipWarnings = skipWarnings;
        return this;
    }

    @Nonnull
    @Override
    public Update print(Consumer<String> printer) {
        return log(printer);
    }

    @Nonnull
    @Override
    public <T, K> T execute(TryFunction<ResultSet, K, SQLException> valueMapper, TryFunction<Stream<K>, T, SQLException> generatedValuesHandler) {
        requireNonNull(valueMapper, "Generated values mapper must be provided!");
        requireNonNull(generatedValuesHandler, "Generated values handler must be provided");
        useGeneratedKeys = true;
        return jdbcTry(() -> TryBiFunction.<Connection, TryFunction<ResultSet, K, SQLException>, Object, SQLException>of(this::doExecute).andThen(generatedKeys -> generatedValuesHandler.apply(((Stream<K>) generatedKeys).onClose(this::close))).apply(connection, valueMapper));
    }

    @Nonnull
    @Override
    public <T, K> T execute(TryFunction<ResultSet, K, SQLException> valueMapper, TryFunction<Stream<K>, T, SQLException> generatedValuesHandler, String... colNames) {
        this.colNames = requireNonNull(colNames, "Column names must be provided");
        return execute(valueMapper, generatedValuesHandler);
    }

    @Nonnull
    @Override
    public <T, K> T execute(TryFunction<ResultSet, K, SQLException> valueMapper, TryFunction<Stream<K>, T, SQLException> generatedValuesHandler, int... colIndices) {
        this.colIndices = requireNonNull(colIndices, "Column indices must be provided");
        return execute(valueMapper, generatedValuesHandler);
    }

    @Nonnull
    public Long execute() {
        return (long) jdbcTry(() -> batch.length > 1 ? doInTransaction(false, () -> connection, null, conn -> doExecute(connection, rs -> rs)) : doExecute(connection, rs -> rs));
    }

    private <K> Object doExecute(Connection conn, TryFunction<ResultSet, K, SQLException> valueMapper) throws SQLException {
        if (isPrepared = useGeneratedKeys) {
            if (colNames != null && colNames.length != 0) {
                statement = conn.prepareStatement(query, colNames);
            } else if (colIndices != null && colIndices.length != 0) {
                statement = conn.prepareStatement(query, colIndices);
            } else {
                statement = conn.prepareStatement(query, RETURN_GENERATED_KEYS);
            }
        } else {
            statement = (isPrepared = batch != null && batch.length != 0) ? conn.prepareStatement(query) : conn.createStatement();
        }
        setPoolable(isPoolable);
        setTimeout(timeout, unit);
        setEscapeProcessing(isEscaped);
        setSkipWarnings(skipWarnings);
        return isBatch && conn.getMetaData().supportsBatchUpdates() ? useGeneratedKeys ? executeBatchWithGeneratedKeys(valueMapper) : executeBatch() : useGeneratedKeys ? executeSimpleWithGeneratedKeys(valueMapper) : executeSimple();
    }

    private <K> Stream<K> executeSimpleWithGeneratedKeys(TryFunction<ResultSet, K, SQLException> valueMapper) {
        return of(batch).onClose(this::close).reduce(
                new ArrayList<K>(),
                (genKeys, params) -> withStatement(s -> {
                    if (isLarge) {
                        setStatementParameters((PreparedStatement) s, params).executeLargeUpdate();
                    } else {
                        setStatementParameters((PreparedStatement) s, params).executeUpdate();
                    }
                    genKeys.addAll(StreamSupport.stream(new ResultSetSpliterator(s.getGeneratedKeys()), false).map(rs -> jdbcTry(() -> valueMapper.apply(rs))).collect(toList()));
                    return genKeys;
                }),
                (list1, list2) -> {
                    list1.addAll(list2);
                    return list1;
                }
        ).stream();
    }

    private <K> Stream<K> executeBatchWithGeneratedKeys(TryFunction<ResultSet, K, SQLException> valueMapper) {
        return of(batch).onClose(this::close)
                .map(params -> withStatement(statement -> {
                    if (isPrepared) {
                        setStatementParameters((PreparedStatement) statement, params).addBatch();
                    } else {
                        statement.addBatch(query);
                    }
                    return statement;
                }))
                .reduce(
                        new ArrayList<K>(),
                        (genKeys, s) -> jdbcTry(() -> {
                            if (isLarge) {
                                s.executeLargeBatch();
                            } else {
                                s.executeBatch();
                            }
                            genKeys.addAll(StreamSupport.stream(new ResultSetSpliterator(s.getGeneratedKeys()), false).map(rs -> jdbcTry(() -> valueMapper.apply(rs))).collect(toList()));
                            return genKeys;
                        }),
                        (list1, list2) -> {
                            list1.addAll(list2);
                            return list1;
                        }
                ).stream();
    }

    private long executeSimple() {
        return of(batch).onClose(this::close).reduce(0L, (rowsAffected, params) -> rowsAffected += jdbcTry(() -> isLarge ? withStatement(s -> isPrepared ? setStatementParameters((PreparedStatement) s, params).executeLargeUpdate() : s.executeLargeUpdate(query)) : (long) withStatement(s -> isPrepared ? setStatementParameters((PreparedStatement) s, params).executeUpdate() : s.executeUpdate(query))), Long::sum);
    }

    private long executeBatch() {
        return of(batch).onClose(this::close).map(params -> withStatement(statement -> {
            if (isPrepared) {
                setStatementParameters((PreparedStatement) statement, params).addBatch();
            } else {
                statement.addBatch(query);
            }
            return statement;
        })).reduce(0L, (rowsAffected, stmt) -> rowsAffected += stream(jdbcTry(() -> isLarge ? stmt.executeLargeBatch() : stream(stmt.executeBatch()).asLongStream().toArray())).sum(), Long::sum);
    }

    @Override
    PreparedStatement prepareStatement(Connection connection, String query, Object... params) {
        return null;
    }

    @Override
    final String asSQL(String query, Object... params) {
        return stream(params).flatMap(p -> of((Object[]) p)).map(p -> super.asSQL(query, (Object[]) p)).collect(joining(STATEMENT_DELIMITER));
    }

}
