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
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * An abstraction for DML (INSERT/UPDATE/DELETE) statements.<br/>
 * Returns affected rows by this query.<br/>
 * If this is a batch query then affected rows are summed.
 */
@SuppressWarnings("unchecked")
@ParametersAreNonnullByDefault
public interface Update extends Query {

    /**
     * Executes this DML query returning affected row count.<br/>
     * If this query represents a batch then affected rows are summarized for all batches.
     *
     * @return affected rows count
     */
    @Nonnull
    Long execute();

    /**
     * Executes an update query providing generated results.<br/>
     * Autogenerated keys columns are accessible via their indices inside <code>generatedValuesHandler</code>
     * Example:
     * <pre>{@code
     * List<Object> list = db.execute(
     *      rs -> new Object[] {
     *          rs.getLong(1),
     *          rs.getString(2),
     *          rs.getObject(3)
     *      },
     *      streamOfObjects -> streamOfObjects.collect(Collectors.toList())
     * )}</pre>
     *
     * @param valueMapper generated values resultset mapper function
     * @param generatedValuesHandler handler which operates on {@link ResultSet} with generated values
     * @return an arbitrary result from  <code>generatedValuesHandler</code> function
     * @throws NullPointerException if generatedValuesHandler or valueMapper is null
     * @see java.sql.Connection#prepareStatement(String, int) (where <code>int</code> is of <code>Statement.RETURN_GENERATED_KEYS</code>)
     */
    @Nonnull
    <T, K> T execute(TryFunction<ResultSet, K, SQLException> valueMapper, TryFunction<Stream<K>, T, SQLException> generatedValuesHandler);

    /**
     * Executes an update query providing generated results.<br/>
     * Autogenerated keys columns are accessible via their names inside <code>generatedValuesHandler</code>
     * Example:
     * <pre>{@code
     * List<Object> list = db.execute(
     *      rs -> new Object[] {
     *          rs.getLong("id"),
     *          rs.getString("hash"),
     *          rs.getObject("myGeneratedValueColumn")
     *      },
     *      streamOfObjects -> streamOfObjects.collect(Collectors.toList()),
     *      "id", "hash", "myGeneratedValueColumn"
     * )}</pre>
     *
     * @param valueMapper generated values resultset mapper function
     * @param generatedValuesHandler handler which operates on {@link ResultSet} with generated values
     * @param colNames               column names with generated keys
     * @return an arbitrary result from  <code>generatedValuesHandler</code> function
     * @throws NullPointerException     if colNames or generatedValuesHandler or valueMapper is null
     * @throws IllegalArgumentException if colNames is empty
     * @see java.sql.Connection#prepareStatement(String, String[])
     */
    @Nonnull
    <T, K> T execute(TryFunction<ResultSet, K, SQLException> valueMapper, TryFunction<Stream<K>, T, SQLException> generatedValuesHandler, String... colNames);

    /**
     * Executes an update query providing generated results.<br/>
     * Autogenerated keys columns are accessible via their indices inside <code>generatedValuesHandler</code>
     * Example:
     * <pre>{@code
     * List<Object> list = db.execute(
     *      rs -> new Object[] {
     *          rs.getLong(1),
     *          rs.getString(2),
     *          rs.getObject(5)
     *      },
     *      streamOfObjects -> streamOfObjects.collect(Collectors.toList()),
     *      1, 2, 5
     * )}</pre>
     *
     * @param valueMapper generated values resultset mapper function
     * @param generatedValuesHandler handler which operates on {@link ResultSet} with generated values
     * @param colIndices             indices of the columns with generated keys
     * @return an arbitrary result from  <code>generatedValuesHandler</code> function
     * @throws NullPointerException     if colIndices or generatedValuesHandler or valueMapper is null
     * @throws IllegalArgumentException if colIndices is empty
     * @see java.sql.Connection#prepareStatement(String, int[])
     */
    @Nonnull
    <T, K> T execute(TryFunction<ResultSet, K, SQLException> valueMapper, TryFunction<Stream<K>, T, SQLException> generatedValuesHandler, int... colIndices);

    /**
     * Tells this update will be a large update
     *
     * @return update query abstraction
     * @see PreparedStatement#executeLargeUpdate()
     */
    Update large(boolean isLarge);

    /**
     * Tells DB to use batch (if possible)
     *
     * @return update query abstraction
     * @see DatabaseMetaData#supportsBatchUpdates()
     */
    Update batched(boolean isBatch);

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    default Update timeout(int timeout) {
        return timeout(timeout, TimeUnit.SECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    Update timeout(int timeout, TimeUnit unit);

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    Update poolable(boolean poolable);

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    Update escaped(boolean escapeProcessing);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    Update skipWarnings(boolean skipWarnings);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    Update print(Consumer<String> printer);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    default Update print() {
        return print(System.out::println);
    }

}
