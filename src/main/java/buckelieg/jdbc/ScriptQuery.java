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

import buckelieg.jdbc.fn.TrySupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static buckelieg.jdbc.Utils.*;
import static java.lang.Math.max;
import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

@NotThreadSafe
@ParametersAreNonnullByDefault
final class ScriptQuery<T extends Map.Entry<String, ?>> implements Script {

    private static final Consumer<SQLException> NOOP = e -> {
        // do nothing
    };

    private final String script;
    private final TrySupplier<Connection, SQLException> connectionSupplier;
    private final List<T> params;
    private String query;
    private final ExecutorService conveyor;
    private final ConcurrentMap<String, RSMeta.Column> metaCache;
    private int timeout;
    private TimeUnit unit = TimeUnit.SECONDS;
    private Consumer<String> logger;
    private boolean escaped = true;
    private boolean skipErrors = true;
    private boolean skipWarnings = true;
    private boolean poolable;
    private Consumer<SQLException> errorHandler = NOOP;

    /**
     * Creates script executor query
     *
     * @param connectionSupplier db connection supplier function
     * @param script     an arbitrary SQL script to execute
     * @throws IllegalArgumentException in case of corrupted script (like illegal comment lines encountered)
     */
    ScriptQuery(ExecutorService conveyor, ConcurrentMap<String, RSMeta.Column> metaCache, TrySupplier<Connection, SQLException> connectionSupplier, String script, @Nullable Iterable<T> namedParams) {
        this.conveyor = conveyor;
        this.metaCache = metaCache;
        this.connectionSupplier = connectionSupplier;
        this.script = script;
        this.params = namedParams == null ? emptyList() : StreamSupport.stream(namedParams.spliterator(), false).collect(Collectors.toList());
    }

    /**
     * Executes script. All comments are cut out.
     * Therefore all RDBMS-specific hints are ignored (like Oracle's <code>APPEND</code>) etc.
     *
     * @return a time, taken by this script to complete in milliseconds
     * @throws SQLRuntimeException in case of any errors including {@link SQLWarning} (if corresponding option is set) OR (if timeout is set) - in case of execution run out of time.
     */
    @Nonnull
    @Override
    public Long execute() {
        try {
            if (timeout == 0) {
                return doExecute();
            } else {
                try {
                    return conveyor.submit(this::doExecute).get(timeout, unit);
                } catch (Exception e) {
                    throw new SQLException(e);
                }
            }
        } catch (Exception e) {
            throw newSQLRuntimeException(e);
        } finally {
            close();
        }
    }

    private long doExecute() throws SQLException {
        return doInTransaction(false, connectionSupplier, null, conn -> {
            long start = currentTimeMillis();
            for (String query : script.split(STATEMENT_DELIMITER)) {
                try {
                    if (isAnonymous(query)) {
                        executeQuery(new QueryImpl(conveyor, () -> conn, query));
                    } else {
                        Map.Entry<String, Object[]> preparedQuery = prepareQuery(query, params);
                        if (isProcedure(preparedQuery.getKey())) {
                            new StoredProcedureQuery(conveyor, metaCache, () -> conn, preparedQuery.getKey(), stream(preparedQuery.getValue()).map(p -> p instanceof P ? (P<?>) p : P.in(p)).toArray(P[]::new)).skipWarnings(skipWarnings).print(this::log).call();
                        } else {
                            executeQuery(new QueryImpl(conveyor, () -> conn, preparedQuery.getKey(), preparedQuery.getValue()));
                        }
                    }
                } catch (Exception e) {
                    if (skipErrors) {
                        errorHandler.accept(new SQLException(e));
                    } else {
                        throw newSQLRuntimeException(e);
                    }
                }
            }
            return currentTimeMillis() - start;
        });
    }

    private void executeQuery(Query query) {
        query.escaped(escaped).poolable(poolable).skipWarnings(skipWarnings).print(this::log).execute();
    }

    private void log(String query) {
        if (logger != null) logger.accept(query);
    }

    @Nonnull
    @Override
    public Script escaped(boolean escapeProcessing) {
        this.escaped = escapeProcessing;
        return this;
    }

    @Nonnull
    @Override
    public Script print(Consumer<String> printer) {
        requireNonNull(printer, "Printer must be provided").accept(asSQL());
        return this;
    }

    @Nonnull
    @Override
    public Script skipErrors(boolean skipErrors) {
        this.skipErrors = skipErrors;
        return this;
    }

    @Nonnull
    @Override
    public Script skipWarnings(boolean skipWarnings) {
        this.skipWarnings = skipWarnings;
        return this;
    }

    @Nonnull
    @Override
    public Script timeout(int timeout, TimeUnit unit) {
        this.timeout = max(timeout, 0);
        this.unit = requireNonNull(unit, "Time Unit must be provided");
        return this;
    }

    @Nonnull
    @Override
    public Script errorHandler(Consumer<SQLException> handler) {
        this.errorHandler = requireNonNull(handler, "Error handler must be provided");
        return this;
    }

    @Override
    public String toString() {
        return asSQL();
    }

    @Nonnull
    @Override
    public Script poolable(boolean poolable) {
        this.poolable = poolable;
        return this;
    }

    @Nonnull
    @Override
    public Script verbose(Consumer<String> logger) {
        this.logger = requireNonNull(logger, "Logger must be provided");
        return this;
    }

    @Nonnull
    @Override
    public String asSQL() {
        if (query == null) {
            Map.Entry<String, Object[]> preparedScript = prepareQuery(script, params);
            query = Utils.asSQL(preparedScript.getKey(), preparedScript.getValue());
        }
        return query;
    }

}
