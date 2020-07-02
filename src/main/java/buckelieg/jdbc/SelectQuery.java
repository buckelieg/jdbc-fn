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
import buckelieg.jdbc.fn.TryFunction;
import buckelieg.jdbc.fn.TryQuadConsumer;
import buckelieg.jdbc.fn.TryTriConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;
import java.sql.*;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static buckelieg.jdbc.Utils.setStatementParameters;
import static java.lang.Math.max;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;

@NotThreadSafe
@ParametersAreNonnullByDefault
class SelectQuery extends AbstractQuery<Statement> implements Iterable<ResultSet>, Iterator<ResultSet>, Spliterator<ResultSet>, Select {

    abstract static class SelectForInsert<T> implements Select.ForInsert<T> {
        protected Consumer<T> insertedHandler;
        protected Consumer<String> logger;

        @Nonnull
        @Override
        public Select.ForInsert<T> verbose(Consumer<String> logger) {
            this.logger = requireNonNull(logger, "Logger must be provided");
            return this;
        }

        @Nonnull
        @Override
        public Select.ForInsert<T> onInserted(Consumer<T> handler) {
            this.insertedHandler = requireNonNull(handler, "Inserted handler must be provided");
            return this;
        }

    }

    abstract static class SelectForUpdate<T> implements Select.ForUpdate<T> {

        protected Consumer<String> logger;
        protected BiConsumer<T, T> updatedHandler;

        @Override
        @Nonnull
        public Select.ForUpdate<T> onUpdated(BiConsumer<T, T> handler) {
            updatedHandler = requireNonNull(handler, "Updated handler must be provided");
            return this;
        }

        @Nonnull
        @Override
        public Select.ForUpdate<T> verbose(Consumer<String> logger) {
            this.logger = requireNonNull(logger, "Logger must be provided");
            return this;
        }
    }

    abstract static class SelectForDelete<T> implements Select.ForDelete<T> {
        protected Consumer<T> deletedHandler;
        protected Consumer<String> logger;

        @Nonnull
        @Override
        public Select.ForDelete<T> verbose(Consumer<String> logger) {
            this.logger = requireNonNull(logger, "Logger must be rovided");
            return this;
        }

        @Nonnull
        @Override
        public Select.ForDelete<T> onDeleted(Consumer<T> handler) {
            this.deletedHandler = requireNonNull(handler, "Deleted handler must be provided");
            return this;
        }
    }

    protected final Executor conveyor;
    protected final ConcurrentMap<String, RSMeta.Column> metaCache;
    protected int currentResultSetNumber = 1;
    ResultSet rs;
    ResultSet wrapper;
    private boolean isMutable = false;
    private boolean hasNext;
    private boolean hasMoved;
    private int fetchSize;
    private int maxRowsInt;
    private long maxRowsLong;

    SelectQuery(Executor conveyor, ConcurrentMap<String, RSMeta.Column> metaCache, Connection connection, String query, Object... params) {
        super(connection, query, params);
        this.conveyor = conveyor;
        this.metaCache = metaCache;
    }

    @Override
    @Nonnull
    public final Iterator<ResultSet> iterator() {
        return this;
    }

    @Override
    public final boolean hasNext() {
        if (hasMoved) {
            return hasNext;
        }
        hasNext = doHasNext();
        hasMoved = true;
        if (!hasNext) {
            close();
        }
        return hasNext;
    }

    protected boolean doHasNext() {
        return jdbcTry(() -> rs != null && rs.next());
    }

    @Override
    public final ResultSet next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        hasMoved = false;
        return wrapper;
    }

    @Nonnull
    @Override
    public <T> ForInsert<T> forInsert(TryFunction<ResultSet, T, SQLException> mapper, TryTriConsumer<T, ResultSet, Metadata, SQLException> inserter) {
        requireNonNull(inserter, "Insert function must be provided");
        return new SelectForInsert<T>() {
            @Nonnull
            @Override
            public Stream<T> execute(Collection<T> toInsert) {
                requireNonNull(toInsert, "Insert collection must be provided");
                isMutable = true;
                Stream<T> stream = SelectQuery.this.execute(mapper);
                wrapper = new MutableResultSet(rs);
                Metadata meta = new RSMeta(connection, rs, metaCache);
                return toInsert.isEmpty() ? stream : jdbcTry(() -> {
                    rs.moveToInsertRow();
                    for (T row : toInsert) {
                        inserter.accept(row, wrapper, meta);
                        if (((MutableResultSet) wrapper).updated) {
                            rs.insertRow();
                            if (insertedHandler != null) {
                                conveyor.execute(() -> insertedHandler.accept(row));
                            }
                            if (logger != null) {
                                logger.accept(row.toString());
                            }
                            ((MutableResultSet) wrapper).updated = false;
                        }
                    }
                    rs.moveToCurrentRow();
                    return stream;
                });
            }
        };
    }

    @Nonnull
    @Override
    public <T> ForDelete<T> forDelete(TryFunction<ResultSet, T, SQLException> mapper, TryFunction<T, ?, SQLException> keyExtractor) {
        requireNonNull(keyExtractor, "Key extractor function must be provided");
        return new SelectForDelete<T>() {
            @Nonnull
            @Override
            public Stream<T> execute(Collection<T> toDelete) {
                requireNonNull(toDelete, "Delete collection must be provided");
                isMutable = true;
                return doDelete(SelectQuery.this.execute(mapper), logger, deletedHandler, toDelete, keyExtractor);
            }
        };
    }

    @Nonnull
    @Override
    public ForDelete<Map<String, Object>> forDelete() {
        return new SelectForDelete<Map<String, Object>>() {
            @Nonnull
            @Override
            public Stream<Map<String, Object>> execute(Collection<Map<String, Object>> toDelete) {
                requireNonNull(toDelete, "Delete collection must be provided");
                isMutable = true;
                Stream<Map<String, Object>> stream = SelectQuery.this.execute();
                RSMeta meta = new RSMeta(connection, rs, metaCache);
                return doDelete(stream, logger, deletedHandler, toDelete, row -> meta.getPrimaryKeys().stream().map(pk -> new SimpleImmutableEntry<>(pk, row.get(pk))).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
            }
        };
    }

    private <T> Stream<T> doDelete(Stream<T> stream, @Nullable Consumer<String> logger, @Nullable Consumer<T> deletedHandler, Collection<T> toDelete, TryFunction<T, ?, SQLException> keyExtractor) {
        List<T> excluded = new ArrayList<>(toDelete.size());
        AtomicBoolean isRemovable = new AtomicBoolean(true);
        return toDelete.isEmpty() ? stream : stream.filter(row -> jdbcTry(() -> {
            Iterator<T> it = toDelete.iterator();
            while (it.hasNext()) {
                T deleted = it.next();
                if (!excluded.contains(deleted)) {
                    Object pkOld = keyExtractor.apply(row);
                    Object pkNew = keyExtractor.apply(deleted);
                    if (pkOld.equals(pkNew)) {
                        excluded.add(deleted);
                        rs.deleteRow();
                        if (isRemovable.get()) {
                            try {
                                it.remove();
                            } catch (UnsupportedOperationException e) {
                                isRemovable.set(false); // no more tries to remove elements from the source collection
                            }
                        }
                        if (deletedHandler != null) {
                            conveyor.execute(() -> deletedHandler.accept(row));
                        }
                        if (logger != null) {
                            logger.accept(row.toString());
                        }
                        return false;
                    }
                }
            }
            return true;
        }));
    }

    @Nonnull
    @Override
    public <T> ForUpdate<T> forUpdate(TryFunction<ResultSet, T, SQLException> mapper, TryQuadConsumer<T, T, ResultSet, Metadata, SQLException> updater) {
        requireNonNull(updater, "Updater must be provided");
        return new SelectForUpdate<T>() {
            @Nonnull
            @Override
            public Stream<T> execute(Collection<T> toUpdate) {
                requireNonNull(toUpdate, "Update collection must be provided");
                List<T> exclude = new ArrayList<>(toUpdate.size());
                isMutable = true;
                AtomicBoolean isRemovable = new AtomicBoolean(true);
                Stream<T> stream = SelectQuery.this.execute(mapper);
                wrapper = new MutableResultSet(rs);
                Metadata meta = new RSMeta(connection, rs, metaCache);
                return toUpdate.isEmpty() ? stream : stream.map(row -> jdbcTry(() -> {
                    Iterator<T> it = toUpdate.iterator();
                    while (it.hasNext()) {
                        T updated = it.next();
                        if (!exclude.contains(updated)) {
                            updater.accept(row, updated, wrapper, meta);
                            if (((MutableResultSet) wrapper).updated) {
                                rs.updateRow();
                                ((MutableResultSet) wrapper).updated = false;
                                exclude.add(updated);
                                if (isRemovable.get()) {
                                    try {
                                        it.remove();
                                    } catch (UnsupportedOperationException e) {
                                        e.printStackTrace();
                                        isRemovable.set(false); // no more tries to remove elements from the source collection
                                    }
                                }
                                if (updatedHandler != null) {
                                    conveyor.execute(() -> updatedHandler.accept(row, updated));
                                }
                                if (logger != null) {
                                    logger.accept(updated.toString());
                                }
                                return updated;
                            }
                        }
                    }
                    return row;
                }));
            }
        };
    }

    @Nonnull
    @Override
    public ForUpdate<Map<String, Object>> forUpdate() {
        return new SelectForUpdate<Map<String, Object>>() {
            @Nonnull
            @Override
            public Stream<Map<String, Object>> execute(Collection<Map<String, Object>> toUpdate) {
                requireNonNull(toUpdate, "Update collection must be provided");
                isMutable = true;
                Stream<Map<String, Object>> stream = SelectQuery.this.execute();
                Metadata meta = new RSMeta(connection, rs, metaCache);
                Map<String, Set<Object>> primaryKeys = jdbcTry(() -> meta.getPrimaryKeys().stream()
                        .collect(toMap(pk -> pk, pk -> toUpdate.stream().map(row -> row.get(pk)).collect(toSet())))
                );
                List<String> updatableColumnNames = meta.getColumnNames().stream().filter(col -> !meta.isPrimaryKey(col)).collect(toList());
                return toUpdate.isEmpty() ? stream : stream.map(row -> jdbcTry(() -> {
                    Map<String, Object> updated = toUpdate.stream().filter(upd -> {
                        for (Map.Entry<String, Object> e : upd.entrySet()) {
                            Set<Object> keys = primaryKeys.get(e.getKey());
                            if (keys != null && keys.contains(row.get(e.getKey()))) {
                                Object oldKey = row.get(e.getKey());
                                return oldKey != null && oldKey.equals(e.getValue());
                            }
                        }
                        return false;
                    }).findFirst().orElse(emptyMap());
                    if (!updated.isEmpty()) {
                        Map<String, Object> newRow = new LinkedHashMap<>(row);
                        boolean needsUpdate = false;
                        for (String colName : updatableColumnNames) {
                            Object newValue = updated.get(colName);
                            Object oldValue = row.get(colName);
                            if (!(oldValue == null && newValue == null) && (newValue != null && !newValue.equals(oldValue))) {
                                rs.updateObject(colName, newValue);
                                newRow.put(colName, newValue);
                                needsUpdate = true;
                            }
                        }
                        if (needsUpdate) {
                            rs.updateRow();
                            if (updatedHandler != null) {
                                conveyor.execute(() -> updatedHandler.accept(row, newRow));
                            }
                            if (logger != null) {
                                logger.accept(newRow.toString());
                            }
                            return newRow;
                        }
                    }
                    return row;
                }));
            }
        };
    }

    @Nonnull
    @Override
    public final <T> Stream<T> execute(TryBiFunction<ResultSet, Integer, T, SQLException> mapper) {
        requireNonNull(mapper, "Mapper must be provided");
        return StreamSupport.stream(jdbcTry(() -> {
            connection.setAutoCommit(false);
            statement = prepareStatement();
            setPoolable();
            setEscapeProcessing();
            setTimeout();
            statement.setFetchSize(fetchSize); // 0 value is ignored by Statement.setFetchSize;
            if (maxRowsInt != -1) {
                statement.setMaxRows(maxRowsInt);
            }
            if (maxRowsLong != -1L) {
                statement.setLargeMaxRows(maxRowsLong);
            }
            doExecute();
            if (rs != null) {
                wrapper = new ImmutableResultSet(rs);
            }
            return this;
        }), false).map(rs -> jdbcTry(() -> mapper.apply(wrapper, currentResultSetNumber))).onClose(this::close);
    }

    protected void doExecute() throws SQLException {
        rs = isPrepared ? ((PreparedStatement) statement).executeQuery() : statement.execute(query) ? statement.getResultSet() : null;
    }

    @Nonnull
    @Override
    public final Select fetchSize(int size) {
        this.fetchSize = max(0, fetchSize);
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

    @Nonnull
    @Override
    public final Select poolable(boolean poolable) {
        return setPoolable(poolable);
    }

    @Nonnull
    @Override
    public final Select timeout(int timeout, TimeUnit unit) {
        return setTimeout(timeout, unit);
    }

    @Nonnull
    @Override
    public final Select escaped(boolean escapeProcessing) {
        return setEscapeProcessing(escapeProcessing);
    }

    @Nonnull
    @Override
    public Select skipWarnings(boolean skipWarnings) {
        return setSkipWarnings(skipWarnings);
    }

    @Nonnull
    @Override
    public Select print(Consumer<String> printer) {
        return log(printer);
    }

    @Override
    public final Spliterator<ResultSet> spliterator() {
        return this;
    }

    @Override
    public final boolean tryAdvance(Consumer<? super ResultSet> action) {
        requireNonNull(action);
        if (hasNext()) {
            action.accept(next());
            return true;
        }
        return false;
    }

    @Override
    public final Spliterator<ResultSet> trySplit() {
        return null; // not splittable. Parallel streams would not gain any performance benefits.
    }

    @Override
    public final long estimateSize() {
        return Long.MAX_VALUE;
    }

    @Override
    public final int characteristics() {
        return Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.NONNULL;
    }

    @Override
    public void forEachRemaining(Consumer<? super ResultSet> action) {
        requireNonNull(action);
        while (hasNext()) {
            action.accept(next());
        }
    }

    @Override
    public void close() {
        jdbcTry(() -> connection.setAutoCommit(autoCommit));
        super.close();
    }

    protected Statement prepareStatement() throws SQLException {
        return isPrepared ? setStatementParameters(connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, isMutable ? ResultSet.CONCUR_UPDATABLE : ResultSet.CONCUR_READ_ONLY), params) : connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, isMutable ? ResultSet.CONCUR_UPDATABLE : ResultSet.CONCUR_READ_ONLY);
    }

}
