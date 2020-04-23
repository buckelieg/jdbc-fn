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
import java.io.File;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static buckelieg.jdbc.fn.Utils.defaultMapper;
import static java.util.Objects.requireNonNull;

/**
 * Helper class which simplifies common JDBC scenarios
 */
@ParametersAreNonnullByDefault
public final class Queries {

    private Queries() {
        throw new UnsupportedOperationException("No instances.");
    }

    private static volatile DB db;

    public static synchronized void setConnection(Connection connection) {
        Queries.db = new DB(requireNonNull(connection, "Connection must be provided."));
    }

    /**
     * Obtains a <code>{@link List}</code> of values from provided connection.
     *
     * @param conn   connection to a database
     * @param mapper <code>{@link ResultSet}</code> to value mapper
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     * @return a <code>{@link List}</code> of mapped values.
     */
    @Nonnull
    public static <T> List<T> list(Connection conn, TryFunction<ResultSet, T, SQLException> mapper, String query, Object... params) {
        return new DB(conn).select(query, params).list(mapper);
    }

    /**
     * Obtains a <code>{@link List}</code> of values from previously provided connection.<br/>
     * The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method.
     *
     * @param mapper <code>{@link ResultSet}</code> to value mapper
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     * @return a <code>{@link List}</code> of mapped values.
     */
    @Nonnull
    public static <T> List<T> list(TryFunction<ResultSet, T, SQLException> mapper, String query, Object... params) {
        return db().select(query, params).list(mapper);
    }

    /**
     * Obtains a <code>{@link List}</code> of Maps from provided connection.
     *
     * @param conn   connection to a database
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     * @return a <code>{@link List}</code> of maps.
     */
    @Nonnull
    public static List<Map<String, Object>> list(Connection conn, String query, Object... params) {
        return list(conn, new Utils.DefaultMapper(), query, params);
    }

    /**
     * Obtains a <code>{@link List}</code> of Maps from previously provided connection.<br/>
     * The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method.
     *
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     * @return a <code>{@link List}</code> of maps.
     */
    @Nonnull
    public static List<Map<String, Object>> list(String query, Object... params) {
        return list(new Utils.DefaultMapper(), query, params);
    }

    /**
     * Obtains a single value from provided connection.
     *
     * @param conn   connection to a database
     * @param mapper <code>{@link ResultSet}</code> to value mapper
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     * @return an <code>{@link Optional}</code> holding mapped value (or not)
     */
    @Nonnull
    public static <T> Optional<T> single(Connection conn, TryFunction<ResultSet, T, SQLException> mapper, String query, Object... params) {
        return new DB(conn).select(query, params).single(mapper);
    }

    /**
     * Obtains a single value from previously provided connection.<br/>
     * The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method.
     *
     * @param mapper <code>{@link ResultSet}</code> to value mapper
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     * @return an <code>{@link Optional}</code> holding mapped value (or not)
     */
    @Nonnull
    public static <T> Optional<T> single(TryFunction<ResultSet, T, SQLException> mapper, String query, Object... params) {
        return db().select(query, params).single(mapper);
    }

    /**
     * Obtains a single value from provided connection.
     *
     * @param conn   connection to a database
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     * @return an <code>{@link Optional}</code> holding mapped value (or not)
     */
    @Nonnull
    public static Optional<Map<String, Object>> single(Connection conn, String query, Object... params) {
        return single(conn, defaultMapper, query, params);
    }

    /**
     * Obtains a single value from previously provided connection.<br/>
     * The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method.
     *
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     * @return an <code>{@link Optional}</code> holding mapped value (or not)
     */
    @Nonnull
    public static Optional<Map<String, Object>> single(String query, Object... params) {
        return single(defaultMapper, query, params);
    }

    /**
     * Executes provided DML statement on the provided connection.
     *
     * @param conn   connection to a database
     * @param query  a DML query to execute against provided connection
     * @param params query parameters (if any)
     * @return affected rows count
     */
    public static long update(Connection conn, String query, Object params) {
        return new DB(conn).update(query, params).execute();
    }

    /**
     * Executes provided DML statement on the previously provided connection.<br/>
     * The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method.
     *
     * @param query  a DML query to execute against provided connection
     * @param params query parameters (if any)
     * @return affected rows count
     */
    public static long update(String query, Object params) {
        return db().update(query, params).execute();
    }

    /**
     * Calls stored procedure on provided connection.
     *
     * @param conn   connection to a database
     * @param mapper <code>{@link ResultSet}</code> to value mapper
     * @param query  a query to execute against provided connection (must conform procedure call syntax)
     * @param params procedure IN parameters (if any)
     * @return an <code>{@link Optional}</code> holding mapped value (or not)
     */
    @Nonnull
    public static <T> Optional<T> call(Connection conn, TryFunction<CallableStatement, T, SQLException> mapper, String query, Object... params) {
        return new DB(conn).procedure(query, params).call(mapper);
    }

    /**
     * Calls stored procedure for a single result on provided connection.<br/>
     * Procedure is MUST not have any arguments and return results as a <code>{@link ResultSet}</code> object.<br/>
     *
     * @param conn   connection to a database
     * @param mapper <code>{@link ResultSet}</code> to value mapper
     * @param query  a procedure call query (must conform syntax) to execute against provided connection
     * @return a <code>{@link List}</code> of mapper values or empty
     */
    @Nonnull
    public static <T> List<T> callForList(Connection conn, TryFunction<ResultSet, T, SQLException> mapper, String query) {
        return new DB(conn).procedure(query).list(mapper);
    }

    /**
     * Calls stored procedure for a single result on previously provided connection.<br/>
     * Procedure is MUST not have any arguments and return results as a <code>{@link ResultSet}</code> object.<br/>
     * The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method.
     *
     * @param mapper <code>{@link ResultSet}</code> to value mapper
     * @param query  a procedure call query (must conform syntax) to execute against previously provided connection
     * @return a <code>{@link List}</code> of mapper values or empty
     */
    @Nonnull
    public static <T> List<T> callForList(TryFunction<ResultSet, T, SQLException> mapper, String query) {
        return db().procedure(query).list(mapper);
    }

    /**
     * Calls stored procedure on provided connection that is with no results expected.
     *
     * @param conn   connection to a database
     * @param query  a query to execute against provided connection (must conform procedure call syntax)
     * @param params procedure IN parameters (if any)
     */
    public static void call(Connection conn, String query, Object... params) {
        new DB(conn).procedure(query, params).call();
    }

    /**
     * Calls stored procedure on previously provided connection that is with no results expected.<br/>
     * The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method.
     *
     * @param query  a query to execute against provided connection (must conform procedure call syntax)
     * @param params procedure IN parameters (if any)
     */
    public static void call(String query, Object... params) {
        db().procedure(query, params).call();
    }

    /**
     * Executes an arbitrary query on the provided connection.
     *
     * @param conn   connection to a database
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     */
    public static void execute(Connection conn, String query, Object... params) {
        new DB(conn).query(query, params).execute();
    }

    /**
     * Executes an arbitrary query on the previously provided connection.<br/>
     * The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method.
     *
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     */
    public static void execute(String query, Object... params) {
        db().query(query, params).execute();
    }

    /**
     * Executes an arbitrary script on the provided connection.
     *
     * @param conn   connection to a database
     * @param script a script to execute against provided connection
     */
    public static void execute(Connection conn, String script, Map<String, ?> params) {
        new DB(conn).script(script, params).execute();
    }

    /**
     * Executes an arbitrary script on the provided connection.<br/>
     * The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method.
     *
     * @param script a script to execute against previously provided connection
     */
    public static void execute(File script) {
        db().script(script).execute();
    }

    private static DB db() {
        return requireNonNull(db, "Provide connection first using setConnection(Connection) method!");
    }

}
