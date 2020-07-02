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

import buckelieg.jdbc.Utils.DefaultMapper;
import buckelieg.jdbc.fn.TryFunction;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

@SuppressWarnings({"unchecked", "rawtypes"})
@NotThreadSafe
@ParametersAreNonnullByDefault
final class StoredProcedureQuery extends SelectQuery implements StoredProcedure {

    private TryFunction<CallableStatement, ?, SQLException> mapper;
    private Consumer consumer;

    StoredProcedureQuery(Executor conveyor, ConcurrentMap<String, RSMeta.Column> metaCache, Connection connection, String query, P<?>... params) {
        super(conveyor, metaCache, connection, query, params);
    }

    @Nonnull
    @Override
    public <T> Select call(TryFunction<CallableStatement, T, SQLException> mapper, Consumer<T> consumer) {
        this.mapper = requireNonNull(mapper, "Mapper must be provided");
        this.consumer = requireNonNull(consumer, "Consumer must be provided");
        return this;
    }

    @Nonnull
    @Override
    public Stream<Map<String, Object>> execute() {
        AtomicReference<TryFunction<ResultSet, Map<String, Object>, SQLException>> mapper = new AtomicReference<>(new DefaultMapper());
        return execute((rs, i) -> {
            if(i != currentResultSetNumber) {
                mapper.set(new DefaultMapper());
            }
            return mapper.get().apply(rs);
        });
    }

    @Nonnull
    @Override
    public StoredProcedure skipWarnings(boolean skipWarnings) {
        return setSkipWarnings(skipWarnings);
    }

    @Nonnull
    @Override
    public StoredProcedure print(Consumer<String> printer) {
        return log(printer);
    }

    @Override
    protected void doExecute() throws SQLException {
        rs = (isPrepared ? ((CallableStatement)statement).execute() : statement.execute(query)) ? statement.getResultSet() : null;
    }

    protected boolean doHasNext() {
        return jdbcTry(() -> {
            boolean moved = super.doHasNext();
            if (!moved) {
                if (statement.getMoreResults()) {
                    if (rs != null && !rs.isClosed()) {
                        rs.close();
                    }
                    rs = statement.getResultSet();
                    currentResultSetNumber++;
                    wrapper = new ImmutableResultSet(rs);
                    return super.doHasNext();
                }
                try {
                    if (mapper != null && consumer != null && isPrepared) {
                        consumer.accept(jdbcTry(() -> mapper.apply(new ImmutableCallableStatement((CallableStatement) statement))));
                    }
                } finally {
                    close();
                }
            }
            return moved;
        });
    }

    @Override
    protected Statement prepareStatement() throws SQLException {
        if(isPrepared) {
            CallableStatement cs = connection.prepareCall(query);
            for (int i = 1; i <= params.length; i++) {
                P<?> p = (P<?>) params[i - 1];
                if (p.isOut() || p.isInOut()) {
                    SQLType type = requireNonNull(p.getType(), format("Parameter '%s' must have SQLType set", p));
                    try {
                        cs.registerOutParameter(i, type);
                    } catch (SQLFeatureNotSupportedException e) {
                        // fallback to previous version of JDBC
                        cs.registerOutParameter(i, type.getVendorTypeNumber());
                    }
                }
                if (p.isIn() || p.isInOut()) {
                    cs.setObject(i, p.getValue());
                }
            }
            return cs;
        }
        return connection.createStatement();
    }
}
