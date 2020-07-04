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

import buckelieg.jdbc.fn.*;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static buckelieg.jdbc.Utils.*;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * An abstraction for SELECT statement
 */
@SuppressWarnings("unchecked")
@ParametersAreNonnullByDefault
public interface Select extends Query {

    /**
     * An abstraction for <code>INSERT</code> action being performed on {@link ResultSet} object
     *
     * @param <T> inserted item type
     */
    @ParametersAreNonnullByDefault
    interface ForInsert<T> {

        /**
         *
         * @param item
         * @return
         */
        boolean single(T item);

        /**
         * Executes this <code>INSERT</code> action which is performed on a {@link ResultSet} object obtained via provided <code>SELECT</code> statement
         *
         * @param toInsert a collection of items to insert
         * @return an updated {@link Stream} of items (which includes inserted items)
         * @throws NullPointerException if provided collection is null
         * @see ResultSet#insertRow()
         */
        @Nonnull
        Stream<T> execute(Collection<T> toInsert);

        /**
         * An alias for {@link #execute(Collection)} method
         *
         * @param toInsert a collection of items to insert
         * @return an updated {@link Stream} of items (which includes inserted items)
         * @throws NullPointerException if provided collection is null
         * @see #execute(Collection)
         */
        @Nonnull
        default Stream<T> stream(Collection<T> toInsert) {
            return execute(toInsert);
        }

        /**
         * Shorthand for stream mapping for list
         *
         * @param toInsert a collection of items to insert
         * @return a {@link List} of updated items (which includes inserted items)
         * @throws NullPointerException if provided collection is null
         * @see #execute(Collection)
         * @see Stream#collect(Collector)
         * @see java.util.stream.Collectors#toList
         */
        @Nonnull
        default List<T> list(Collection<T> toInsert) {
            return execute(toInsert).collect(toList());
        }

        /**
         * An item inserted event handler (executes in a separate thread)
         *
         * @param handler a handler to be invoked whenever the item is inserted
         * @return an abstraction for <code>INSERT</code> action being performed on {@link ResultSet} object
         * @throws NullPointerException if provided handler is null
         */
        @Nonnull
        ForInsert<T> onInserted(Consumer<T> handler);

        /**
         * @param logger item {@link String} representation consumer
         * @return an abstraction for <code>INSERT</code> action being performed on {@link ResultSet} object
         * @throws NullPointerException if provided logger is null
         */
        @Nonnull
        ForInsert<T> verbose(Consumer<String> logger);

        /**
         * @return an abstraction for <code>INSERT</code> action being performed on {@link ResultSet} object
         * @see #verbose(Consumer)
         * @see System#out
         * @see PrintStream#println(String)
         */
        @Nonnull
        default ForInsert<T> verbose() {
            return verbose(System.out::println);
        }
    }

    /**
     * An abstraction for <code>UPDATE</code> action being performed on {@link ResultSet} object
     *
     * @param <T> updated item type
     */
    @ParametersAreNonnullByDefault
    interface ForUpdate<T> {

        /**
         *
         * @param item
         * @return
         */
        boolean single(T item);

        /**
         * Executes this <code>UPDATE</code> action which is performed on a {@link ResultSet} object obtained via provided <code>SELECT</code> statement
         *
         * @param toUpdate a collection of items to update with
         * @return an updated {@link Stream} of items
         * @throws NullPointerException if provided collection is null
         */
        @Nonnull
        Stream<T> execute(Collection<T> toUpdate);

        /**
         * An alias for {@link #execute(Collection)} method
         *
         * @param toUpdate a collection of items to update with
         * @return an updated {@link Stream} of items
         * @throws NullPointerException if provided collection is null
         */
        @Nonnull
        default Stream<T> stream(Collection<T> toUpdate) {
            return execute(toUpdate);
        }

        /**
         * Shorthand for stream mapping for list
         *
         * @param toUpdate a collection of items to update with
         * @return an updated {@link List} of items
         * @throws NullPointerException if provided collection is null
         * @see #execute(Collection)
         * @see Stream#collect(Collector)
         * @see java.util.stream.Collectors#toList
         */
        @Nonnull
        default List<T> list(Collection<T> toUpdate) {
            return stream(toUpdate).collect(toList());
        }

        /**
         * @param handler a handler to be invoked whenever the item is updated
         * @return an abstraction for <code>UPDATE</code> action being performed on {@link ResultSet} object
         * @throws NullPointerException if provided handler is null
         */
        @Nonnull
        ForUpdate<T> onUpdated(BiConsumer<T, T> handler);

        /**
         * @param logger item {@link String} representation consumer
         * @return an abstraction for <code>UPDATE</code> action being performed on {@link ResultSet} object
         * @throws NullPointerException if provided logger is null
         */
        @Nonnull
        ForUpdate<T> verbose(Consumer<String> logger);

        /**
         * @return an abstraction for <code>UPDATE</code> action being performed on {@link ResultSet} object
         * @see #verbose(Consumer)
         * @see System#out
         * @see PrintStream#println(String)
         */
        @Nonnull
        default ForUpdate<T> verbose() {
            return verbose(System.out::println);
        }

    }

    /**
     * An abstraction for <code>DELETE</code> action being performed on {@link ResultSet} object
     *
     * @param <T> deleted item type
     */
    @ParametersAreNonnullByDefault
    interface ForDelete<T> {

        /**
         *
         * @param item
         * @return
         */
        boolean single(T item);

        /**
         * Executes this <code>DELETE</code> action which is performed on a {@link ResultSet} object obtained via provided <code>SELECT</code> statement
         *
         * @param toDelete a collection of items to delete
         * @return an updated {@link Stream} of items (excluding deleted ones)
         * @throws NullPointerException if provided collection is null
         */
        @Nonnull
        Stream<T> execute(Collection<T> toDelete);

        /**
         * An alias for {@link #execute(Collection)} method
         *
         * @param toDelete a collection of items to delete
         * @return an updated {@link Stream} of items (excluding deleted ones)
         * @throws NullPointerException if provided collection is null
         */
        @Nonnull
        default Stream<T> stream(Collection<T> toDelete) {
            return execute(toDelete);
        }

        /**
         * Shorthand for stream mapping for list
         *
         * @param toDelete a collection of items to delete
         * @return an updated {@link List} of items (excluding deleted ones)
         * @throws NullPointerException if provided collection is null
         * @see #execute(Collection)
         * @see Stream#collect(Collector)
         * @see java.util.stream.Collectors#toList
         */
        @Nonnull
        default List<T> list(Collection<T> toDelete) {
            return execute(toDelete).collect(toList());
        }

        /**
         * @param handler a handler to be invoked whenever the item is deleted
         * @return an abstraction for <code>DELETE</code> action being performed on {@link ResultSet} object
         * @throws NullPointerException if provided handler is null
         */
        @Nonnull
        ForDelete<T> onDeleted(Consumer<T> handler);

        /**
         * @param logger item {@link String} representation consumer
         * @return an abstraction for <code>DELETE</code> action being performed on {@link ResultSet} object
         * @throws NullPointerException if provided logger is null
         */
        @Nonnull
        ForDelete<T> verbose(Consumer<String> logger);

        /**
         * @return an abstraction for <code>DELETE</code> action being performed on {@link ResultSet} object
         * @see #verbose(Consumer)
         * @see System#out
         * @see PrintStream#println(String)
         */
        @Nonnull
        default ForDelete<T> verbose() {
            return verbose(System.out::println);
        }

    }

    /**
     * @param mapper  a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
     * @param updater item update function
     * @param <T>     item type
     * @return an abstraction for <code>UPDATE</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    <T> ForUpdate<T> forUpdate(TryFunction<ResultSet, T, SQLException> mapper, TryQuadConsumer<T, T, ResultSet, Metadata, SQLException> updater);

    /**
     * @param mapper  a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
     * @param updater item update function
     * @param <T>     item type
     * @return an abstraction for <code>UPDATE</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    default <T> ForUpdate<T> forUpdate(TryFunction<ResultSet, T, SQLException> mapper, TryTriConsumer<T, T, ResultSet, SQLException> updater) {
        return forUpdate(mapper, (original, updated, rs, meta) -> updater.accept(original, updated, rs));
    }

    /**
     * @param mapper  a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
     * @param updater item update function
     * @param <T>     item type
     * @return an abstraction for <code>UPDATE</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    default <T> ForUpdate<T> forUpdate(TryFunction<ResultSet, T, SQLException> mapper, TryBiConsumer<T, ResultSet, SQLException> updater) {
        return forUpdate(mapper, (original, updated, rs, meta) -> updater.accept(updated, rs));
    }

    /**
     * @param updater item update function
     * @return an abstraction for <code>UPDATE</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    default ForUpdate<Map<String, Object>> forUpdate(TryQuadConsumer<Map<String, Object>, Map<String, Object>, ResultSet, Metadata, SQLException> updater) {
        return forUpdate(new DefaultMapper(), updater);
    }

    /**
     * @param updater item update function
     * @return an abstraction for <code>UPDATE</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    default ForUpdate<Map<String, Object>> forUpdate(TryTriConsumer<Map<String, Object>, Map<String, Object>, ResultSet, SQLException> updater) {
        return forUpdate((original, updated, rs, meta) -> updater.accept(original, updated, rs));
    }

    /**
     * @param updater item update function
     * @return an abstraction for <code>UPDATE</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    default ForUpdate<Map<String, Object>> forUpdate(TryBiConsumer<Map<String, Object>, ResultSet, SQLException> updater) {
        return forUpdate((original, updated, rs) -> updater.accept(updated, rs));
    }

    /**
     * @return an abstraction for <code>UPDATE</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    ForUpdate<Map<String, Object>> forUpdate();

    /**
     * @param mapper   a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
     * @param inserter insert item function
     * @param <T>      item type
     * @return an abstraction for <code>INSERT</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    <T> ForInsert<T> forInsert(TryFunction<ResultSet, T, SQLException> mapper, TryTriConsumer<T, ResultSet, Metadata, SQLException> inserter);

    /**
     * @param mapper   a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
     * @param inserter insert item function
     * @param <T>      item type
     * @return an abstraction for <code>INSERT</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    default <T> ForInsert<T> forInsert(TryFunction<ResultSet, T, SQLException> mapper, TryBiConsumer<T, ResultSet, SQLException> inserter) {
        return forInsert(mapper, (row, rs, meta) -> inserter.accept(row, rs));
    }

    /**
     * @param inserter insert item function
     * @return an abstraction for <code>INSERT</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    default ForInsert<Map<String, Object>> forInsert(TryTriConsumer<Map<String, Object>, ResultSet, Metadata, SQLException> inserter) {
        return forInsert(new DefaultMapper(), inserter);
    }

    /**
     * @param inserter insert item function
     * @return an abstraction for <code>INSERT</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    default ForInsert<Map<String, Object>> forInsert(TryBiConsumer<Map<String, Object>, ResultSet, SQLException> inserter) {
        return forInsert((row, rs, meta) -> inserter.accept(row, rs));
    }

    /**
     * @return an abstraction for <code>INSERT</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    ForInsert<Map<String, Object>> forInsert();

    /**
     * @param mapper       a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
     * @param keyExtractor
     * @param <T>          item type
     * @return an abstraction for <code>DELETE</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    <T> ForDelete<T> forDelete(TryFunction<ResultSet, T, SQLException> mapper, TryFunction<T, ?, SQLException> keyExtractor);

    /**
     * @param keyExtractor
     * @return an abstraction for <code>DELETE</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    default ForDelete<Map<String, Object>> forDelete(TryFunction<Map<String, Object>, ?, SQLException> keyExtractor) {
        return forDelete(new DefaultMapper(), keyExtractor);
    }

    /**
     * @return an abstraction for <code>DELETE</code> action being performed on {@link ResultSet} object
     */
    @Nonnull
    ForDelete<Map<String, Object>> forDelete();

    /**
     * In cases when single result of SELECT statement is expected
     * <br/>Like <code>SELECT COUNT(*) FROM TABLE_NAME</code> etc.
     *
     * @param mapper a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
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
     * @param mapper a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
     * @param <T>    item type
     * @return a {@link Stream} over mapped {@link ResultSet}
     */
    @Nonnull
    <T> Stream<T> execute(TryBiFunction<ResultSet, Integer, T, SQLException> mapper);

    /**
     * Executes this SELECT statement returning a <code>Stream</code> of mapped values over <code>ResultSet</code> object
     * <br/>Note:
     * Whenever we left stream without calling some 'reduction' (terminal) operation we left resource freeing to JDBC
     * <br/><code>stream().iterator().next()</code>
     * <br/>Thus there could be none or some rows more, but result set (and a statement) would not be closed forcibly
     * <br/>In such cases we rely on JDBC resources auto closing mechanism
     * <br/>And it is strongly recommended to use {@link #single(TryFunction)} method for the cases above
     *
     * @param mapper a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
     * @return a {@link Stream} over mapped {@link ResultSet}
     * @throws NullPointerException if mapper is null
     * @throws SQLRuntimeException  as a wrapper for {@link SQLException}
     * @see #execute()
     */
    @Nonnull
    default <T> Stream<T> execute(TryFunction<ResultSet, T, SQLException> mapper) {
        return execute((rs, i) -> mapper.apply(rs));
    }

    /**
     * An alias for {@link #execute(TryBiFunction)} method
     *
     * @param mapper a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
     * @return a {@link Stream} over mapped {@link ResultSet}
     * @see #execute(TryFunction)
     */
    @Nonnull
    default <T> Stream<T> stream(TryBiFunction<ResultSet, Integer, T, SQLException> mapper) {
        return execute(mapper);
    }

    /**
     * An alias for {@link #execute(TryFunction)} method
     *
     * @param mapper a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
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
     * Shorthand for stream mapping to list
     *
     * @param mapper a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
     * @return a {@link List} over mapped {@link ResultSet}
     * @see Stream#collect(Collector)
     * @see java.util.stream.Collectors#toList
     */
    @Nonnull
    default <T> List<T> list(TryBiFunction<ResultSet, Integer, T, SQLException> mapper) {
        return stream(mapper).collect(toList());
    }

    /**
     * Shorthand for stream mapping to list
     *
     * @param mapper a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
     * @return a {@link List} over mapped {@link ResultSet}
     * @see Stream#collect(Collector)
     * @see java.util.stream.Collectors#toList
     */
    @Nonnull
    default <T> List<T> list(TryFunction<ResultSet, T, SQLException> mapper) {
        return stream(mapper).collect(toList());
    }

    /**
     * Shorthand for stream mapping for list
     *
     * @return a {@link Map} with key-value pairs
     * @see #list(TryFunction)
     */
    @Nonnull
    default List<Map<String, Object>> list() {
        return execute().collect(toList());
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
