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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static buckelieg.jdbc.fn.Utils.*;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * An abstraction for SELECT statement
 */
@SuppressWarnings("unchecked")
@ParametersAreNonnullByDefault
public interface Select extends Query {

    /**
     * In cases when single result of SELECT statement is expected
     * <br/>Like <code>SELECT COUNT(*) FROM TABLE_NAME</code> etc.
     *
     * @param mapper ResultSet mapper function
     * @throws NullPointerException if mapper is null
     * @see #execute(TryFunction)
     */
    @Nonnull
    default <T> Optional<T> single(TryFunction<ResultSet, T, SQLException> mapper) {
        T result;
        try {
            result = fetchSize(1).maxRows(1).list(mapper).iterator().next();
        } catch (NoSuchElementException e) {
            result = null;
        } catch (Exception e) {
            throw newSQLRuntimeException(e);
        }
        return ofNullable(result);
    }

    /**
     * Executes SELECT statement for SINGLE result with default mapper applied
     *
     * @return an {@link Optional} with the {@link Map} as a value
     */
    @Nonnull
    default Optional<Map<String, Object>> single() {
        return single(defaultMapper);
    }

    /**
     * Executes this SELECT statement returning a <code>Stream</code> of mapped values as <code>Map</code>s
     * <br/>Note:
     * Whenever we left stream without calling some 'reduction' (terminal) operation we left resource freeing to JDBC
     * <br/><code>stream().iterator().next()</code>
     * <br/>Thus there could be none or some rows more, but result set (and a statement) would not be closed forcibly
     * <br/>In such cases we rely on JDBC resources auto closing mechanism
     * <br/>And it is strongly recommended to use {@link #single()} method for the cases above
     *
     * @return a {@link Stream} of {@link Map}s
     * @see #execute(TryFunction)
     */
    @Nonnull
    default Stream<Map<String, Object>> execute() {
        return execute(new DefaultMapper());
    }

    /**
     * Executes this SELECT statement returning a <code>Stream</code> of mapped values over <code>ResultSet</code> object
     * <br/>Note:
     * Whenever we left stream without calling some 'reduction' (terminal) operation we left resource freeing to JDBC
     * <br/><code>stream().iterator().next()</code>
     * <br/>Thus there could be none or some rows more, but result set (and a statement) would not be closed forcibly
     * <br/>In such cases we rely on JDBC resources auto closing mechanism
     * <br/>And it is strongly recommended to use {@link #single(TryFunction)} method for the cases above
     *
     * @param mapper result set mapper which is not required to handle {@link SQLException}
     * @return a {@link Stream} over mapped {@link ResultSet}
     * @throws NullPointerException if mapper is null
     * @throws SQLRuntimeException  as a wrapper for {@link SQLException}
     * @see #execute()
     */
    @Nonnull
    <T> Stream<T> execute(TryFunction<ResultSet, T, SQLException> mapper);

    /**
     * An alias for {@link #execute(TryFunction)} method
     *
     * @param mapper result set mapper which is not required to handle {@link SQLException}
     * @return a {@link Stream} over mapped {@link ResultSet}
     * @see #execute(TryFunction)
     */
    @Nonnull
    default <T> Stream<T> stream(TryFunction<ResultSet, T, SQLException> mapper) {
        return execute(mapper);
    }

    /**
     * An alias for {@link #execute()} method
     *
     * @return a {@link Stream} of {@link Map}s
     * @see #execute()
     */
    @Nonnull
    default Stream<Map<String, Object>> stream() {
        return execute();
    }

    /**
     * Shorthand for stream mapping for list
     *
     * @param mapper result set mapper which is not required to handle {@link SQLException}
     * @return a {@link List} over mapped {@link ResultSet}
     */
    @Nonnull
    default <T> List<T> list(TryFunction<ResultSet, T, SQLException> mapper) {
        return execute(mapper).collect(toList());
    }

    /**
     * Shorthand for stream mapping for list
     *
     * @return a {@link Map} with key-value pairs
     */
    @Nonnull
    default List<Map<String, Object>> list() {
        return list(new DefaultMapper());
    }

    /**
     * Configures {@link java.sql.Statement} fetch size parameter
     *
     * @param size desired fetch size. Should be greater than 0
     * @return select query abstraction
     * @see java.sql.Statement#setFetchSize(int)
     * @see ResultSet#setFetchSize(int)
     */
    @Nonnull
    Select fetchSize(int size);

    /**
     * Updates max rows obtained with this query
     *
     * @param max rows number limit
     * @return select query abstraction
     * @see java.sql.Statement#setMaxRows(int)
     */
    @Nonnull
    Select maxRows(int max);

    /**
     * Updates max rows obtained with this query
     *
     * @param max rows number limit
     * @return select query abstraction
     * @see java.sql.Statement#setLargeMaxRows(long)
     */
    @Nonnull
    Select maxRows(long max);

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    default Select timeout(int timeout) {
        return timeout(timeout, TimeUnit.SECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    Select timeout(int timeout, TimeUnit unit);


    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    Select poolable(boolean poolable);

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    Select escaped(boolean escapeProcessing);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    Select skipWarnings(boolean skipWarnings);

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    Select print(Consumer<String> printer);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    default Select print() {
        return print(System.out::println);
    }

}
