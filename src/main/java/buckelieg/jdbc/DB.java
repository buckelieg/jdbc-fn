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

import buckelieg.jdbc.fn.TryFunction;
import buckelieg.jdbc.fn.TrySupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.ThreadSafe;
import javax.sql.DataSource;
import java.io.File;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.function.BiFunction;

import static buckelieg.jdbc.Utils.*;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;
import static java.util.AbstractMap.SimpleImmutableEntry;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.of;

/**
 * Database query factory
 *
 * @see AutoCloseable
 * @see Query
 * @see Select
 * @see Update
 * @see StoredProcedure
 * @see Script
 */
@ThreadSafe
@ParametersAreNonnullByDefault
public final class DB implements AutoCloseable {

    private Connection connection;
    private final TrySupplier<Connection, SQLException> connectionSupplier;
    private ExecutorService conveyor;
    private boolean shutdownConveyor = true;
    private ConcurrentMap<String, RSMeta.Column> metaCache;

    DB(Connection connection) {
        this(() -> connection);
    }

    private DB(ExecutorService conveyor, ConcurrentMap<String, RSMeta.Column> metaCache, Connection connection, TrySupplier<Connection, SQLException> connectionSupplier) {
        this.connection = connection;
        this.connectionSupplier = connectionSupplier;
        this.conveyor = conveyor;
        this.metaCache = metaCache;
        this.shutdownConveyor = false;
    }

    /**
     * Creates DB with connection supplier
     * <br/>This caches provided connection and tries to create new if previous one is closed
     *
     * @param connectionSupplier the connection supplier.
     * @throws NullPointerException if connection provider is null
     */
    public DB(TrySupplier<Connection, SQLException> connectionSupplier) {
        requireNonNull(connectionSupplier, "Connection supplier must be provided");
        this.connectionSupplier = connectionSupplier;
        this.conveyor = Executors.newCachedThreadPool();
        this.metaCache = new ConcurrentHashMap<>();
    }

    /**
     * Creates DB with provided <code>DataSource</code>
     * <br/>This will use <code>getConnection()</code> method to obtain a connection
     *
     * @param ds the DataSource
     * @see DataSource#getConnection()
     */
    public DB(DataSource ds) {
        this(ds::getConnection);
    }

    /**
     * Closes underlying connection
     *
     * @throws SQLRuntimeException if something went wrong
     */
    @Override
    public void close() {
        List<Throwable> exceptions = new ArrayList<>();
        try {
            if (connection != null) connection.close();
        } catch (SQLException e) {
            exceptions.add(e);
        }
        if (conveyor != null && shutdownConveyor) {
            conveyor.shutdown();
            try {
                conveyor.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty()) {
            throw newSQLRuntimeException(exceptions.toArray(new Throwable[0]));
        }
    }


    /**
     * Executes an arbitrary parameterized SQL statement
     * <br/>Parameter names are CASE SENSITIVE!
     * <br/>So that :NAME and :name are two different parameters
     *
     * @param query           an SQL query to execute
     * @param namedParameters query named parameters in the form of :name
     * @return select query
     * @throws IllegalArgumentException either if query string is a procedure call statement or it is not a single SQL statement
     * @see Select
     */
    @Nonnull
    public Query query(String query, Map<String, ?> namedParameters) {
        return query(query, namedParameters.entrySet());
    }

    /**
     * Executes an arbitrary SQL statement with named parameters
     * <br/>Parameter names are CASE SENSITIVE!
     * <br/>So that :NAME and :name are two different parameters
     *
     * @param query           an arbitrary SQL query to execute
     * @param namedParameters query named parameters in the form of :name
     * @return select query
     * @throws IllegalArgumentException either if query string is a procedure call statement or it is not a single SQL statement
     * @see Select
     */
    @SafeVarargs
    @Nonnull
    public final <T extends Entry<String, ?>> Query query(String query, T... namedParameters) {
        return query(query, asList(namedParameters));
    }

    /**
     * Executes a set of an arbitrary SQL statement(s)
     *
     * @param script          (a series of) SQL statement(s) to execute
     * @param namedParameters named parameters to be used in the script
     * @return script query abstraction
     * @throws NullPointerException if script is null
     * @see Script
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nonnull
    public final Script script(String script, Map<String, ?> namedParameters) {
        return new ScriptQuery(getConveyor(), metaCache, getConnection(connectionSupplier), cutComments(requireNonNull(script, "SQL script must be provided")), namedParameters.entrySet());
    }

    /**
     * Executes a set of an arbitrary SQL statement(s)
     *
     * @param script          (a series of) SQL statement(s) to execute
     * @param namedParameters named parameters to be used in the script
     * @return script query abstraction
     * @throws NullPointerException if script is null
     * @see Script
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @SafeVarargs
    @Nonnull
    public final <T extends Entry<String, ?>> Script script(String script, T... namedParameters) {
        return new ScriptQuery(getConveyor(), metaCache, getConnection(connectionSupplier), cutComments(requireNonNull(script, "SQL script must be provided")), asList(namedParameters));
    }

    /**
     * Executes an arbitrary SQL statement(s) with default encoding (<code>Charset.UTF_8</code>)
     *
     * @param source          file with a SQL script contained to execute
     * @param namedParameters named parameters to be used in the script
     * @return script query abstraction
     * @throws RuntimeException in case of any errors (like {@link java.io.FileNotFoundException} or source file is null)
     * @see #script(File, Charset, Entry[])
     */
    @SafeVarargs
    @Nonnull
    public final <T extends Entry<String, ?>> Script script(File source, T... namedParameters) {
        return script(source, UTF_8, namedParameters);
    }

    /**
     * Executes an arbitrary SQL statement(s)
     *
     * @param source          file with a SQL script contained
     * @param encoding        source file encoding to be used
     * @param namedParameters named parameters to be used in the script
     * @return script query abstraction
     * @throws RuntimeException in case of any errors (like {@link java.io.FileNotFoundException} or source file is null)
     * @see #script(String, Entry[])
     * @see Charset
     */
    @SafeVarargs
    @Nonnull
    public final <T extends Entry<String, ?>> Script script(File source, Charset encoding, T... namedParameters) {
        try {
            return script(new String(readAllBytes(requireNonNull(source, "Source file must be provided").toPath()), requireNonNull(encoding, "File encoding must be provided")), namedParameters);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calls stored procedure
     *
     * @param query procedure call string to execute
     * @return stored procedure call
     * @see StoredProcedure
     * @see #procedure(String, P[])
     */
    @Nonnull
    public StoredProcedure procedure(String query) {
        return procedure(query, new Object[0]);
    }

    /**
     * Calls stored procedure
     * <br/>Supplied parameters are considered as IN parameters
     *
     * @param query      procedure call string to execute
     * @param parameters procedure IN parameters' values
     * @return stored procedure call
     * @see StoredProcedure
     * @see #procedure(String, P[])
     */
    @Nonnull
    public StoredProcedure procedure(String query, Object... parameters) {
        return procedure(query, stream(parameters).map(P::in).collect(toList()).toArray(new P<?>[parameters.length]));
    }

    /**
     * Calls stored procedure
     * <br/>Parameter names are CASE SENSITIVE!
     * <br/>So that :NAME and :name are two different parameters
     * <br/>Named parameters order must match parameters type of the procedure called
     *
     * @param query      procedure call string to execute
     * @param parameters procedure parameters as declared (IN/OUT/INOUT)
     * @return stored procedure call
     * @throws IllegalArgumentException if provided query is not valid DML statement or named parameters provided along with unnamed ones
     * @see StoredProcedure
     */
    @Nonnull
    public StoredProcedure procedure(String query, P<?>... parameters) {
        query = checkSingle(requireNonNull(query, "SQL query must be provided"));
        if (isAnonymous(query) && !isProcedure(query)) {
            throw new IllegalArgumentException(format("Query '%s' is not valid procedure call statement", query));
        } else {
            int namedParams = (int) of(parameters).filter(p -> !p.getName().isEmpty()).count();
            if (namedParams == parameters.length && parameters.length > 0) {
                Entry<String, Object[]> preparedQuery = prepareQuery(
                        query,
                        of(parameters)
                                .map(p -> new SimpleImmutableEntry<>(p.getName(), new P<?>[]{p}))
                                .collect(toList())
                );
                query = preparedQuery.getKey();
                parameters = stream(preparedQuery.getValue()).map(p -> (P<?>) p).toArray(P[]::new);
            } else if (0 < namedParams && namedParams < parameters.length) {
                throw new IllegalArgumentException(
                        format(
                                "Cannot combine named parameters(count=%s) with unnamed ones(count=%s).",
                                namedParams, parameters.length - namedParams
                        )
                );
            }
        }
        return new StoredProcedureQuery(getConveyor(), metaCache, getConnection(connectionSupplier), query, parameters);
    }

    /**
     * Executes SELECT statement
     *
     * @param query SELECT query to execute
     * @return select query
     * @throws IllegalArgumentException if provided query is a procedure call statement
     * @see Select
     */
    @Nonnull
    public Select select(String query) {
        return select(query, new Object[0]);
    }

    /**
     * Executes SELECT statement
     *
     * @param query      SELECT query to execute
     * @param parameters query parameters in the declared order of '?'
     * @return select query
     * @throws IllegalArgumentException if provided query is a procedure call statement
     * @see Select
     */
    @Nonnull
    public Select select(String query, Object... parameters) {
        requireNonNull(query, "SQL query must be provided");
        if (isProcedure(query)) {
            throw new IllegalArgumentException(format("Query '%s' is not valid select statement", query));
        }
        return new SelectQuery(getConveyor(), metaCache, getConnection(connectionSupplier), checkAnonymous(checkSingle(query)), parameters);
    }


    /**
     * Executes DML statements: INSERT, UPDATE or DELETE
     *
     * @param query INSERT/UPDATE/DELETE query to execute
     * @param batch an array of query parameters on the declared order of '?'
     * @return update query
     * @throws IllegalArgumentException if provided query is a procedure call statement
     * @see Update
     */
    @Nonnull
    public Update update(String query, Object[]... batch) {
        requireNonNull(query, "SQL query must be provided");
        if (isProcedure(query)) {
            throw new IllegalArgumentException(format("Query '%s' is not valid DML statement", query));
        }
        return new UpdateQuery(getConnection(connectionSupplier), checkAnonymous(checkSingle(query)), batch);
    }

    /**
     * Executes a single SQL query
     *
     * @param query      a single arbitrary SQL query to execute
     * @param parameters query parameters in the declared order of '?'
     * @return an SQL query abstraction
     * @throws IllegalArgumentException either if query string is a procedure call statement or it is not a single SQL statement
     */
    @Nonnull
    public Query query(String query, Object... parameters) {
        requireNonNull(query, "SQL query must be provided");
        if (isProcedure(query)) {
            throw new IllegalArgumentException(format("Query '%s' is not valid SQL statement", query));
        }
        return new QueryImpl(getConnection(connectionSupplier), checkAnonymous(checkSingle(query)), parameters);
    }

    /**
     * Executes SELECT statement
     * <br/>Parameter names are CASE SENSITIVE!
     * <br/>So that :NAME and :name are two different parameters
     *
     * @param query           SELECT query to execute
     * @param namedParameters query named parameters in the form of :name
     * @return select query
     * @throws IllegalArgumentException if provided query is a procedure call statement
     * @see Select
     */
    @Nonnull
    public Select select(String query, Map<String, ?> namedParameters) {
        return select(query, namedParameters.entrySet());
    }

    /**
     * Executes SELECT statement with named parameters
     * <br/>Parameter names are CASE SENSITIVE!
     * <br/>So that :NAME and :name are two different parameters
     *
     * @param query           SELECT query to execute
     * @param namedParameters query named parameters in the form of :name
     * @return select query
     * @throws IllegalArgumentException if provided query is a procedure call statement
     * @see Select
     */
    @SafeVarargs
    @Nonnull
    public final <T extends Entry<String, ?>> Select select(String query, T... namedParameters) {
        return select(query, asList(namedParameters));
    }

    /**
     * Executes statements: INSERT, UPDATE or DELETE
     *
     * @param query INSERT/UPDATE/DELETE query to execute
     * @return update query
     * @throws IllegalArgumentException if provided query is a procedure call statement
     * @see Update
     */
    @Nonnull
    public Update update(String query) {
        return update(query, new Object[0]);
    }

    /**
     * Executes statements: INSERT, UPDATE or DELETE
     *
     * @param query      INSERT/UPDATE/DELETE query to execute
     * @param parameters query parameters on the declared order of '?'
     * @return update query
     * @throws IllegalArgumentException if provided query is a procedure call statement
     * @see Update
     */
    @Nonnull
    public Update update(String query, Object... parameters) {
        return update(query, new Object[][]{parameters});
    }

    /**
     * Executes statements: INSERT, UPDATE or DELETE
     * <br/>Parameter names are CASE SENSITIVE!
     * <br/>So that :NAME and :name are two different parameters
     *
     * @param query           INSERT/UPDATE/DELETE query to execute
     * @param namedParameters query named parameters in the form of :name
     * @return update query
     * @throws IllegalArgumentException if provided query is a procedure call statement
     * @see Update
     */
    @SafeVarargs
    @Nonnull
    public final <T extends Entry<String, ?>> Update update(String query, T... namedParameters) {
        return update(query, asList(namedParameters));
    }

    /**
     * Executes statements: INSERT, UPDATE or DELETE
     * <br/>Parameter names are CASE SENSITIVE!
     * <br/>So that :NAME and :name are two different parameters
     *
     * @param query INSERT/UPDATE/DELETE query to execute
     * @param batch an array of query named parameters in the form of :name
     * @return update query
     * @throws IllegalArgumentException if provided query is a procedure call statement
     * @see Update
     */
    @SafeVarargs
    @Nonnull
    public final Update update(String query, Map<String, ?>... batch) {
        List<Entry<String, Object[]>> params = of(batch).map(np -> prepareQuery(query, np.entrySet())).collect(toList());
        return update(params.get(0).getKey(), params.stream().map(Entry::getValue).collect(toList()).toArray(new Object[params.size()][]));
    }

    /**
     * Creates a transaction for the set of an arbitrary statements with specified isolation level
     * <br/>Example usage:
     * <pre>{@code
     *  // suppose we have to create a bunch of new users with provided names and get the latest with all it's attributes filled in
     *  DB db = new DB(ds);
     *  User latestUser = db.transaction(false, TransactionIsolation.SERIALIZABLE, db1 ->
     *      // here db.equals(db1) will return true
     *      // but if we claim to createNew transaction it will not, because a new connection is obtained and new DB instance is created
     *      // so everything inside a transaction MUST be done through db1 reference since it will operate on newly created connection.
     *      // as the rule of thumb - always use lambda parameter to do the things inside transaction
     *      db1.update("INSERT INTO users(name) VALUES(?)", new Object[][]{{"name1"}, {"name2"}, {"name3"}})
     *        .skipWarnings(false)
     *        .timeout(1, TimeUnit.MINUTES)
     *        .print()
     *        .execute(
     *              rs -> rs.getLong(1),
     *              keys -> db1.select("SELECT * FROM users WHERE id=?", keys.peek(id -> db1.procedure("{call PROCESS_USER_CREATED_EVENT(?)}", id).call()).max(Comparator.comparing(i -> i)).orElse(-1L))
     *                         .print()
     *                         .single(rs -> {
     *                              User u = new User();
     *                              u.setId(rs.getLong("id"));
     *                              u.setName(rs.getString("name"));
     *                              //... fill other user's attributes...
     *                              return user;
     *                          })
     *                  );
     *          )
     *          .orElse(null);
     * }</pre>
     * Note that return value must not be an opened cursor, so that code below will throw an exception of Invalid transaction state - held cursor requires same isolation level:
     * <pre>{@code Stream<String> stream = db.transaction(false, TransactionIsolation.SERIALIZABLE, () -> db.select("SELECT * FROM my_table").execute(rs -> rs.getString(1)));
     * stream.collect(Collectors.toList());}</pre>
     * Unless desired isolation level matches the RDBMS default one
     * <br/>If transaction isolation level is not supported by RDBMS then default one will be used
     *
     * @param createNew whether to create new transaction (implies obtaining new connection if possible) or not.
     * @param level     transaction isolation level (null -> default)
     * @param action    an action to be performed in transaction
     * @return an arbitrary result
     * @throws NullPointerException          if no action is provided
     * @throws UnsupportedOperationException if <code>createNew</code> is <code>true</code> but provided <code>connectionSupplier</code> cannot create new connection
     * @throws IllegalArgumentException      desired transaction isolation level is not supported
     * @see TransactionIsolation
     * @see TryFunction
     */
    @Nullable
    public <T> T transaction(boolean createNew, @Nullable TransactionIsolation level, TryFunction<DB, T, SQLException> action) {
        try {
            return doInTransaction(createNew && connection != null, getConnectionSupplier(connectionSupplier, createNew), level, conn -> requireNonNull(action, "Action must be provided").apply(createNew && connection != null ? new DB(getConveyor(), metaCache, conn, connectionSupplier) : this));
        } catch (SQLException e) {
            throw newSQLRuntimeException(e);
        }
    }

    /**
     * Creates a transaction for the set of an arbitrary statements with default isolation level
     *
     * @param createNew whether to create new transaction (implies getting new connection if possible) or not.
     * @param action    an action to be performed in transaction
     * @return an arbitrary result
     * @throws NullPointerException          if no action is provided
     * @throws UnsupportedOperationException if new connection (if requested) could not be created
     * @see #transaction(boolean, TransactionIsolation, TryFunction)
     */
    @Nullable
    public <T> T transaction(boolean createNew, TryFunction<DB, T, SQLException> action) {
        return transaction(createNew, null, action);
    }

    /**
     * Creates a transaction for the set of an arbitrary statements with default isolation level within the same connection (<code>createNew</code> set to <code>false</code>)
     *
     * @param action an action to be performed in transaction
     * @return an arbitrary result
     * @throws NullPointerException if no action is provided
     * @see #transaction(boolean, TryFunction)
     */
    @Nullable
    public <T> T transaction(TryFunction<DB, T, SQLException> action) {
        return transaction(false, action);
    }

    /**
     * Creates a transaction within the current connection with provided {@link TransactionIsolation} level.
     *
     * @param isolationLevel transaction isolation level to set
     * @param action         an action to be dne in transaction
     * @return an arbitrary result
     * @throws NullPointerException     if no action is provided
     * @throws IllegalArgumentException desired transaction isolation level is not supported
     * @see #transaction(boolean, TransactionIsolation, TryFunction)
     */
    @Nullable
    public <T> T transaction(TransactionIsolation isolationLevel, TryFunction<DB, T, SQLException> action) {
        return transaction(false, isolationLevel, action);
    }

    private Select select(String query, Iterable<? extends Entry<String, ?>> namedParams) {
        return prepare(query, namedParams, this::select);
    }

    private Update update(String query, Iterable<? extends Entry<String, ?>> namedParams) {
        return prepare(query, namedParams, this::update);
    }

    private Query query(String query, Iterable<? extends Entry<String, ?>> namedParams) {
        return prepare(query, namedParams, this::query);
    }

    private <T extends Query> T prepare(String query, Iterable<? extends Entry<String, ?>> namedParams, BiFunction<String, Object[], T> toQuery) {
        Entry<String, Object[]> preparedQuery = prepareQuery(query, namedParams);
        return toQuery.apply(preparedQuery.getKey(), preparedQuery.getValue());
    }

    private TrySupplier<Connection, SQLException> getConnectionSupplier(TrySupplier<Connection, SQLException> supplier, boolean forceNew) {
        return () -> {
            if (forceNew) {
                synchronized (this) {
                    Connection newConnection = requireNonNull(supplier.get(), "Connection supplier must provide a connection");
                    if (connection != null && connection == newConnection) {
                        throw new UnsupportedOperationException("No new connection created");
                    }
                    if (newConnection.isClosed()) {
                        throw new SQLException("Provided connection is already closed");
                    }
                    connection = newConnection;
                }
            } else if (connection == null || connection.isClosed()) {
                synchronized (this) {
                    if (connection == null || connection.isClosed()) {
                        connection = requireNonNull(supplier.get(), "Connection supplier must provide a connection");
                        if (connection.isClosed()) {
                            throw new SQLException("Provided connection is already closed");
                        }
                    }
                }
            }
            return connection;
        };
    }

    private Connection getConnection(TrySupplier<Connection, SQLException> supplier) {
        try {
            return getConnectionSupplier(supplier, false).get();
        } catch (SQLException e) {
            throw newSQLRuntimeException(e);
        }
    }

    private ExecutorService getConveyor() {
        if (conveyor == null || conveyor.isShutdown() || conveyor.isTerminated()) {
            synchronized (this) {
                if (conveyor == null || conveyor.isShutdown() || conveyor.isTerminated()) {
                    conveyor = Executors.newCachedThreadPool();
                }
            }
        }
        return conveyor;
    }

}
