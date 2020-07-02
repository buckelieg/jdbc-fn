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

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static buckelieg.jdbc.Utils.setStatementParameters;
import static java.lang.System.currentTimeMillis;

@SuppressWarnings("unchecked")
final class QueryImpl extends AbstractQuery<Statement> {

    QueryImpl(Connection connection, String query, Object... params) {
        super(connection, query, params);
    }

    /**
     * Executes this arbitrary SQL query
     *
     * @return query execution time in milliseconds
     */
    @Nonnull
    @Override
    public Long execute() {
        long start = currentTimeMillis();
        jdbcTry(() -> {
            statement = isPrepared ? setStatementParameters(connection.prepareStatement(query), params) : connection.createStatement();
            setTimeout();
            setPoolable();
            setEscapeProcessing();
            if (isPrepared) {
                ((PreparedStatement) statement).execute();
            } else {
                statement.execute(query);
            }
        });
        close(); // force closing this statement since we will not process any of its possible results
        return currentTimeMillis() - start;
    }

    @Nonnull
    @Override
    public Query poolable(boolean poolable) {
        return setPoolable(poolable);
    }

    @Nonnull
    @Override
    public Query timeout(int timeout, TimeUnit unit) {
        return setTimeout(timeout, unit);
    }

    @Nonnull
    @Override
    public Query escaped(boolean escapeProcessing) {
        return setEscapeProcessing(escapeProcessing);
    }

    @Nonnull
    @Override
    public Query skipWarnings(boolean skipWarnings) {
        return setSkipWarnings(skipWarnings);
    }

    @Nonnull
    @Override
    public Query print(Consumer<String> printer) {
        return log(printer);
    }
}
