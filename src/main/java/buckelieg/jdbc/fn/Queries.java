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

import static java.util.Objects.requireNonNull;

/**
 * Helper class which simplifies common JDBC scenarios
 * <br/>The simplest usage implies to set a connection:
 * <br/><pre>{@code Connection conn = ...;
 * Queries.setConnection(conn);
 * List<String> list = Queries.list(rs -> rs.getString("name"), "SELECT name FROM my_table");}</pre>
 * Note that connection remains opened after execution and it must be closed explicitly
 */
@ParametersAreNonnullByDefault
public final class Queries {

    private Queries() {
        throw new UnsupportedOperationException("No instances.");
    }

    private static volatile DB db;

    /**
     * Sets connection to be used with connection-less parameters functions
     *
     * @param connection a connection to set (must be not null)
     * @throws NullPointerException if connection is null
     */
    public static synchronized void setConnection(Connection connection) {
        Queries.db = new DB(requireNonNull(connection, "Connection must be provided."));
    }

    /**
     * Obtains a <code>{@link List}</code> of values from provided connection
     *
     * @param conn   connection to a database
     * @param mapper <code>{@link ResultSet}</code> to value mapper
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     * @param <T>    result parameter type
     * @return a <code>{@link List}</code> of mapped values
     */
    @Nonnull
    public static <T> List<T> list(Connection conn, TryFunction<ResultSet, T, SQLException> mapper, String query, Object... params) {
        return new DB(conn).select(query, params).list(mapper);
    }

    /**
     * Obtains a <code>{@link List}</code> of values from previously provided connection
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method
     *
     * @param mapper <code>{@link ResultSet}</code> to value mapper
     * @param query  a query to execute against previously provided connection
     * @param params query parameters (if any)
     * @param <T>    result parameter type
     * @return a <code>{@link List}</code> of mapped values
     */
    @Nonnull
    public static <T> List<T> list(TryFunction<ResultSet, T, SQLException> mapper, String query, Object... params) {
        return db().select(query, params).list(mapper);
    }

    /**
     * Obtains a <code>{@link List}</code> of {@link Map}s from provided connection
     *
     * @param conn   connection to a database
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     * @return a <code>{@link List}</code> of {@link Map}s
     */
    @Nonnull
    public static List<Map<String, Object>> list(Connection conn, String query, Object... params) {
        return new DB(conn).select(query, params).list();
    }

    /**
     * Obtains a <code>{@link List}</code> of {@link Map}s from previously provided connection
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method
     *
     * @param query  a query to execute against previously provided connection
     * @param params query parameters (if any)
     * @return a <code>{@link List}</code> of {@link Map}s
     */
    @Nonnull
    public static List<Map<String, Object>> list(String query, Object... params) {
        return db().select(query, params).list();
    }

    /**
     * Obtains a <code>{@link List}</code> of {@link Map}s from previously provided connection
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method
     *
     * @param mapper <code>{@link ResultSet}</code> to value mapper
     * @param query  a query to execute against previously provided connection
     * @param params query parameters (if any)
     * @param <T>    result parameter type
     * @return a <code>{@link List}</code> of mapped values
     */
    @Nonnull
    public static <T> List<T> list(TryFunction<ResultSet, T, SQLException> mapper, String query, Map<String, ?> params) {
        return db().select(query, params).list(mapper);
    }

    /**
     * Obtains a <code>{@link List}</code> of {@link Map}s from previously provided connection
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method
     *
     * @param query  a query to execute against previously provided connection
     * @param params query parameters (if any)
     * @return a <code>{@link List}</code> of {@link Map}s
     */
    @Nonnull
    public static List<Map<String, Object>> list(String query, Map<String, ?> params) {
        return db().select(query, params).list();
    }

    /**
     * Obtains a <code>{@link List}</code> of {@link Map}s from previously provided connection
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method
     *
     * @param mapper <code>{@link ResultSet}</code> to value mapper
     * @param query  a query to execute against previously provided connection
     * @param params query parameters (if any)
     * @param <T>    result parameter type
     * @return a <code>{@link List}</code> of mapped values
     */
    @SafeVarargs
    @Nonnull
    public static <T, P extends Map.Entry<String, ?>> List<T> list(Connection conn, TryFunction<ResultSet, T, SQLException> mapper, String query, P... params) {
        return db().select(query, params).list(mapper);
    }

    /**
     * Obtains a <code>{@link List}</code> of {@link Map}s from previously provided connection
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method
     *
     * @param query  a query to execute against previously provided connection
     * @param params query parameters (if any)
     * @return a <code>{@link List}</code> of {@link Map}s
     */
    @SafeVarargs
    @Nonnull
    public static <T extends Map.Entry<String, ?>> List<Map<String, Object>> list(String query, T... params) {
        return db().select(query, params).list();
    }

    /**
     * Obtains a single value from provided connection
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
     * Obtains a single value from previously provided connection
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method
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
     * Obtains a single value from provided connection
     *
     * @param conn   connection to a database
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     * @return an <code>{@link Optional}</code> holding mapped value (or not)
     */
    @Nonnull
    public static Optional<Map<String, Object>> single(Connection conn, String query, Object... params) {
        return new DB(conn).select(query, params).single();
    }

    /**
     * Obtains a single value from previously provided connection
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method
     *
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     * @return an <code>{@link Optional}</code> holding mapped value (or not)
     */
    @Nonnull
    public static Optional<Map<String, Object>> single(String query, Object... params) {
        return db().select(query, params).single();
    }

    /**
     * Executes provided DML statement on the provided connection
     *
     * @param conn   connection to a database
     * @param query  a DML query to execute against provided connection
     * @param params query parameters (if any)
     * @return affected rows count
     */
    public static long update(Connection conn, String query, Object... params) {
        return new DB(conn).update(query, params).execute();
    }

    /**
     * Executes provided DML statement on the previously provided connection
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method
     *
     * @param query  a DML query to execute against provided connection
     * @param params query parameters (if any)
     * @return affected rows count
     */
    public static long update(String query, Object... params) {
        return db().update(query, params).execute();
    }

    /**
     * Calls stored procedure on provided connection
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
     * Calls stored procedure for a single result on provided connection
     * <br/>Procedure is MUST not have any arguments and return results as a <code>{@link ResultSet}</code> object
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
     * Calls stored procedure for a single result on previously provided connection
     * <br/>Procedure is MUST not have any arguments and return results as a <code>{@link ResultSet}</code> object.<br/>
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method
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
     * Calls stored procedure on provided connection that is with no results expected
     * <br/> Procedure parameters are considered as <code>IN</code>-mode.
     *
     * @param conn   connection to a database
     * @param query  a query to execute against provided connection (must conform procedure call syntax)
     * @param params procedure IN parameters (if any)
     */
    public static void call(Connection conn, String query, Object... params) {
        new DB(conn).procedure(query, params).call();
    }

    /**
     * Calls stored procedure on previously provided connection that is with no results expected
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method.
     *
     * @param query  a query to execute against provided connection (must conform procedure call syntax)
     * @param params procedure IN parameters (if any)
     */
    public static void call(String query, Object... params) {
        db().procedure(query, params).call();
    }

    /**
     * Executes an arbitrary query on the provided connection
     *
     * @param conn   connection to a database
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     */
    public static void execute(Connection conn, String query, Object... params) {
        new DB(conn).query(query, params).execute();
    }

    /**
     * Executes an arbitrary query on the previously provided connection
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method
     *
     * @param query  a query to execute against provided connection
     * @param params query parameters (if any)
     */
    public static void execute(String query, Object... params) {
        db().query(query, params).execute();
    }

    /**
     * Executes an arbitrary script on the provided connection
     *
     * @param conn   connection to a database
     * @param script a script to execute against provided connection
     */
    public static void execute(Connection conn, String script, Map<String, ?> params) {
        new DB(conn).script(script, params).execute();
    }

    /**
     * Executes an arbitrary script on the provided connection
     * <br/>The connection MUST be set via <code>{@link Queries#setConnection(Connection)}</code> method PRIOR calling this method
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
