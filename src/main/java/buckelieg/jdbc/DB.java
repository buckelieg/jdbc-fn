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

import buckelieg.fn.TrySupplier;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static buckelieg.jdbc.Utils.newSQLRuntimeException;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Database query and session factory
 *
 * @see Session
 * @see Transaction
 */
public final class DB extends Session {

  public static final class Builder {

	private ExecutorService executorService = Executors.newWorkStealingPool();

	private boolean terminateExecutorService;

	private Supplier<String> txIdProvider = () -> UUID.randomUUID().toString();

	private Integer maxConnections;

	/**
	 * Configures the maximum number of connections managed by this database
	 * facade. This limit is independent of any pool limit configured on an
	 * externally supplied {@link DataSource}.
	 *
	 * @param count maximum number of connections; values below one become one
	 * @return this builder
	 */
	public Builder withMaxConnections(int count) {
	  this.maxConnections = Math.max(1, count);
	  return this;
	}

	/**
	 * Configures a {@linkplain DB} instance with executor service provided<br/>
	 * Default is {@linkplain Executors#newWorkStealingPool()}
	 *
	 * @param executorService an {@linkplain ExecutorService} instance
	 * @return a {@linkplain Builder} instance
	 * @throws NullPointerException if {@code executorService} is null
	 */
	public Builder withExecutorService(ExecutorService executorService) {
	  this.executorService = requireNonNull(executorService);
	  return this;
	}

	/**
	 * Configures a {@linkplain DB} instance with executor service termination on close value provided<br/>
	 * Default is {@code false}
	 *
	 * @param terminateExecutorService if {@code true} - then {@linkplain ExecutorService} will be attempted to shut down on {@linkplain DB#close()} method invocation
	 * @return a {@linkplain Builder} instance
	 */
	public Builder withTerminateExecutorServiceOnClose(boolean terminateExecutorService) {
	  this.terminateExecutorService = terminateExecutorService;
	  return this;
	}


	/**
	 * Configures a {@linkplain DB} instance with transaction id provider function provided<br/>
	 * Default generator uses {@linkplain UUID#randomUUID()} to provide a string representation of an id
	 *
	 * @param txIdProvider transaction ID provider function
	 * @throws NullPointerException if {@code txIdProvider} is null
	 * @implNote provider function is responsible for control possible value uniqueness or other necessary things
	 */
	public Builder withTransactionIdProvider(Supplier<String> txIdProvider) {
	  this.txIdProvider = requireNonNull(txIdProvider, "Transaction ID provider function must be provided");
	  return this;
	}

	/**
	 * Builds a new <code>DB</code> instance with provided connection supplier function<br/>
	 * Example:
	 * <pre>{@code
	 * DB db = DB.builder().build(driverManager -> driverManager
	 *     .withUser("user")
	 *     .withPassword("password")
	 *     .build("jdbc:vendor:database"));
	 * }</pre>
	 *
	 * @param configurator a function that configures the DriverManager-backed data source
	 * @return a new {@linkplain DB} instance. Never null
	 * @throws NullPointerException if {@code configurator} is null
	 */
	public DB build(Consumer<DriverManagerDataSourceBuilder> configurator) {
	  DriverManagerDataSourceBuilder ds = new DriverManagerDataSourceBuilder();
	  requireNonNull(configurator).accept(ds);
	  return new DB(
			  new ConcurrentHashMap<>(),
			  () -> requireNonNull(txIdProvider.get(), "Transaction ID must not be null"),
			  new DefaultConnectionManager(
					  ds::getConnection,
					  null == maxConnections ? ds.getMaxConnections() : maxConnections,
					  ds.getKeepAliveDuration(),
					  ds.getKeepAliveQuery(),
					  ds.getKeepAliveDuration()
			  ),
			  executorService,
			  terminateExecutorService,
			  true
	  );
	}

	/**
	 * Builds a database facade from a connection provider. Connections supplied
	 * through this overload are managed by jdbc-fn's internal pool and are
	 * closed when this database facade is closed.
	 * <p>
	 * Vendor-specific streaming is selected lazily from metadata on an acquired
	 * connection, so the provider does not need to expose its JDBC URL.
	 *
	 * @param connectionProvider provider of new or externally pooled connections
	 * @return a configured database facade
	 * @throws NullPointerException if {@code connectionProvider} is null
	 */
	public DB build(TrySupplier<Connection, SQLException> connectionProvider) {
	  TrySupplier<Connection, SQLException> provider = requireNonNull(
			  connectionProvider,
			  "Connection provider must be provided"
	  );
	  return new DB(
			  new ConcurrentHashMap<>(),
			  () -> requireNonNull(txIdProvider.get(), "Transaction ID must not be null"),
			  new DefaultConnectionManager(
					  provider,
					  null == maxConnections ? Runtime.getRuntime().availableProcessors() : maxConnections,
					  Duration.ofSeconds(10),
					  null,
					  Duration.ofSeconds(10)
			  ),
			  executorService,
			  terminateExecutorService,
			  true
	  );
	}

	/**
	 * Builds a database facade backed by an externally managed {@link DataSource}.
	 * The data source does not need to expose its JDBC URL: vendor-specific
	 * streaming is selected lazily from {@link java.sql.DatabaseMetaData} on an
	 * acquired connection.
	 *
	 * @param dataSource data source supplying database connections
	 * @return a configured database facade
	 * @throws NullPointerException if {@code dataSource} is null
	 */
	public DB build(DataSource dataSource) {
	  try {
		DataSource source = requireNonNull(dataSource, "Data source must be provided");
		Duration timeout = Duration.ofSeconds(source.getLoginTimeout());
		return new DB(
				new ConcurrentHashMap<>(),
				() -> requireNonNull(txIdProvider.get(), "Transaction ID must not be null"),
				new DefaultConnectionManager(
						source::getConnection,
						null == maxConnections ? Runtime.getRuntime().availableProcessors() : maxConnections,
						timeout,
						null,
						timeout
				),
				executorService,
				terminateExecutorService,
				false
		);
	  } catch (SQLException e) {
		throw newSQLRuntimeException(e);
	  }
	}

  }


  private final Supplier<String> txIdProvider;

  private final ConnectionManager connectionManager;

  private final ExecutorService conveyor;

  private final boolean terminateExecutorServiceOnClose;

  private final boolean terminateConnectionPoolOnClose;

  DB(
		  Map<String, MetadataImpl.Column> metaCache,
		  Supplier<String> txIdProvider,
		  ConnectionManager connectionManager,
		  ExecutorService executorService,
		  boolean terminateExecutorServiceOnClose,
		  boolean terminateConnectionPoolOnClose) {
	super(metaCache, connectionManager::getConnection, connectionManager::close, executorService);
	this.txIdProvider = txIdProvider;
	this.connectionManager = connectionManager;
	this.terminateExecutorServiceOnClose = terminateExecutorServiceOnClose;
	this.terminateConnectionPoolOnClose = terminateConnectionPoolOnClose;
	this.conveyor = executorService;
  }

  public static Builder builder() {
	return new Builder();
  }

  /**
   * Creates a transaction for the set of an arbitrary statements
   * <br/>Example usage:
   * <pre>{@code
   *  // suppose we have to create a bunch of new users with provided names and get the latest one with all it's attributes filled in
   *  DB db = // create DB instance
   *  User latestUser = db.transaction().isolation(Transaction.Isolation.SERIALIZABLE).execute(session ->
   *      session.update("INSERT INTO users(name) VALUES(?)", new Object[][]{{"name1"}, {"name2"}, {"name3"}})
   *        .skipWarnings(false)
   *        .timeout(1, TimeUnit.MINUTES)
   *        .print() // prints to System.out
   *        .execute(rs -> rs.getLong(1)) // returns a collection of generated ids
   *        .stream()
   *        .peek(id -> session.procedure("{call PROCESS_USER_CREATED_EVENT(?)}", id).call())
   *        .max(Comparator.comparing(Function.identity()))
   *        .flatMap(id -> session.select("SELECT * FROM users WHERE id=?", id)
   *                              .print(LOG::debug)
   *                              .single(rs -> new User(rs.getLong("id"), rs.getString("name")))
   *        )
   *        .map(user -> {
   *          // fill other user attributes e.g.
   *          session.select("SELECT * FROM USER_ATTR WHERE user_id = ?", user.getId())
   *                 .execute(rs -> new UserAttr(rs.getLong("id"), rs.getString("name"), rs.getObject("value")))
   *                 .forEach(user::addAttr);
   *           return user;
   *        })
   *        .orElse(null)
   * );
   * }</pre>
   *
   * @return a transaction instance
   */
  public Transaction transaction() {
	return new JDBCTransaction(
			executorService,
			txIdProvider,
			metaCache,
			connectionManager::getConnection,
			connectionManager::close,
			connectionManager::close
	);
  }

  /**
   * Closes this instance of DB. This includes:<br/>
   * <ul>
   *     <li>closing underlying connection(s) pool {@linkplain ConnectionManager#close()}</li>
   *     <li>closing underlying executor service (if requested: {@linkplain DB.Builder#withTerminateExecutorServiceOnClose(boolean)})</li>
   * </ul>
   *
   * @throws SQLRuntimeException if something went wrong
   */
  public void close() {
	if (terminateConnectionPoolOnClose) {
	  try {
		connectionManager.close();
	  } catch (SQLException e) {
		throw newSQLRuntimeException(e);
	  }
	}
	if (terminateExecutorServiceOnClose) conveyor.shutdownNow();
  }

  /**
   * An alias for <pre>{@code DB.builder().build(connectionProvider)}</pre>
   * This implies next defaults:<br/>
   * <ul>
   *     <li>Transaction ID provider: {@linkplain UUID#randomUUID()}</li>
   *     <li>Executor service provider: {@linkplain Executors#newWorkStealingPool}</li>
   *     <li>Maximum acquired connection limit: {@linkplain Runtime#availableProcessors()}</li>
   * </ul>
   *
   * @param connectionProvider a connection supplier function
   * @return a new DB instance. Never null
   * @throws NullPointerException if {@code connectionProvider} is null
   */
  public static DB create(TrySupplier<Connection, SQLException> connectionProvider) {
	return new DB(
			new ConcurrentHashMap<>(),
			() -> UUID.randomUUID().toString(),
			new DefaultConnectionManager(
					connectionProvider,
					Runtime.getRuntime().availableProcessors(),
					Duration.ofSeconds(10),
					null,
					Duration.ofSeconds(10)
			),
			Executors.newWorkStealingPool(),
			true,
			true
	);
  }

  /**
   * Builds a new <code>DB</code> instance with provided connection supplier function<br/>
   * Example:
   * <pre>{@code
   * DataSource ds = // obtain datasource instance (via JNDI, DriverManager etc.)
   * DB db = DB.builder().build(ds::getConnection);
   * // or
   * DB db = DB.builder.build(() -> DriverManager.getConnection("jdbcURL"))
   * }</pre>
   *
   * @param dataSource a {@linkplain DataSource} that returns a connection to database
   * @return a new {@linkplain DB} instance. Never null
   * @throws NullPointerException if {@code connectionProvider} is null
   */
  public static DB create(DataSource dataSource) {
	try {
	  return new DB(
			  new ConcurrentHashMap<>(),
			  () -> format("%s@%s", UUID.randomUUID(), dataSource),
			  new DefaultConnectionManager(
					  dataSource::getConnection,
					  Runtime.getRuntime().availableProcessors(),
					  Duration.ofSeconds(dataSource.getLoginTimeout()),
					  null,
					  Duration.ofSeconds(dataSource.getLoginTimeout())
			  ),
			  Executors.newWorkStealingPool(),
			  true,
			  false
	  );
	} catch (SQLException e) {
	  throw newSQLRuntimeException(e);
	}
  }

}
