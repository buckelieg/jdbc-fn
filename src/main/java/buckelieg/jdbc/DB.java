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
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.ThreadSafe;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

/**
 * Database query and session factory
 *
 * @see Session
 * @see Transaction
 */
@ThreadSafe
@ParametersAreNonnullByDefault
public final class DB extends Session {

  private final Supplier<String> txIdProvider;

  private final ConnectionManager connectionManager;

  private final Supplier<ExecutorService> executorServiceProvider;

  private final AtomicReference<ExecutorService> conveyor = new AtomicReference<>();

  private final boolean terminateConveyorOnClose;

  private final boolean terminateConnectionPoolOnClose;

  private DB(
		  Map<String, RSMeta.Column> metaCache,
		  Supplier<String> txIdProvider,
		  ConnectionManager connectionManager,
		  Supplier<ExecutorService> executorServiceSupplier,
		  boolean terminateConveyorOnClose,
		  boolean terminateConnectionPoolOnClose) {
	super(metaCache, connectionManager::getConnection, connectionManager::close, executorServiceSupplier);
	this.txIdProvider = txIdProvider;
	this.connectionManager = connectionManager;
	this.executorServiceProvider = () -> getExecutorService(executorServiceSupplier);
	this.terminateConveyorOnClose = terminateConveyorOnClose;
	this.terminateConnectionPoolOnClose = terminateConnectionPoolOnClose;
  }

  private ExecutorService getExecutorService(Supplier<ExecutorService> executorServiceSupplier) {
	return conveyor.updateAndGet(conveyor -> {
	  if (conveyor == null || conveyor.isShutdown() || conveyor.isTerminated()) {
		conveyor = requireNonNull(executorServiceSupplier.get(), "Executor service instance must be provided");
	  }
	  return conveyor;
	});
  }

  /**
   * Creates a transaction for the set of an arbitrary statements
   * <br/>Example usage:
   * <pre>{@code
   *  // suppose we have to create a bunch of new users with provided names and get the latest one with all it's attributes filled in
   *  DB db = // create DB instance
   *  User latestUser = db.transaction().isolation(Transaction.Isolation.SERIALIZABLE).apply(session ->
   *      session.update("INSERT INTO users(name) VALUES(?)", new Object[][]{{"name1"}, {"name2"}, {"name3"}})
   *        .skipWarnings(false)
   *        .timeout(1, TimeUnit.MINUTES)
   *        .print() // prints to System.out
   *        .execute(rs -> rs.getLong(1)) // returns a collection of generated ids
   *        .stream()
   *        .peek(id -> session.procedure("{call PROCESS_USER_CREATED_EVENT(?)}", id).call())
   *        .max(Comparator.comparing(i -> i))
   *        .flatMap(id -> session.select("SELECT * FROM users WHERE id=?", id).print().single(rs -> {
   *              User u = new User();
   *              u.setId(rs.getLong("id"));
   *              u.setName(rs.getString("name"));
   *              // ...fill other user's attributes...
   *              return user;
   *        }))
   *        .orElse(null)
   * );
   * }</pre>
   *
   * @return a transaction instance
   */
  @Nonnull
  public Transaction transaction() {
	return new JDBCTransaction(executorServiceProvider, txIdProvider, metaCache, connectionManager::getConnection, connectionManager::close);
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
	if (null != conveyor && terminateConveyorOnClose) {
	  Optional.ofNullable(conveyor.get()).ifPresent(ExecutorService::shutdownNow);
	}
	if (terminateConnectionPoolOnClose) {
	  try {
		connectionManager.close();
	  } catch (SQLException e) {
		throw Utils.newSQLRuntimeException(e);
	  }
	}
  }

  /**
   * A DB instance builder
   */
  @ParametersAreNonnullByDefault
  public static final class Builder {

	private Supplier<String> txIdProvider = () -> UUID.randomUUID().toString();

	private Supplier<ExecutorService> executorServiceSupplier = Executors::newWorkStealingPool;

	private int maxConnections = Runtime.getRuntime().availableProcessors();

	private boolean terminateExecutorServiceOnClose = false;

	private boolean terminateConnectionPoolOnClose = true;

	private ConnectionManager connectionManager;

	private Builder() {
	}

	/**
	 * Configures a {@linkplain DB} instance with transaction id provider function provided<br/>
	 * Default generator uses {@linkplain UUID#randomUUID()} to provide a string representation of an id
	 *
	 * @param txIdProvider transaction ID provider function
	 * @return a {@linkplain Builder} instance
	 * @throws NullPointerException if {@code txIdProvider} is null
	 */
	@Nonnull
	public Builder withTransactionIdProvider(Supplier<String> txIdProvider) {
	  this.txIdProvider = requireNonNull(txIdProvider, "Transaction ID provider function must be provided");
	  return this;
	}

	/**
	 * Configures a {@linkplain DB} instance with executor service provider function provided<br/>
	 * Default provider is {@linkplain Executors#newWorkStealingPool()}
	 *
	 * @param executorServiceProvider an {@linkplain ExecutorService} provider function
	 * @return a {@linkplain Builder} instance
	 * @throws NullPointerException if {@code executorServiceProvider} is null
	 */
	@Nonnull
	public Builder withExecutorServiceProvider(Supplier<ExecutorService> executorServiceProvider) {
	  this.executorServiceSupplier = requireNonNull(executorServiceProvider, "Executor service must be provided");
	  return this;
	}

	/**
	 * Configures a {@linkplain DB} instance with executor service termination on close value provided<br/>
	 * Default is {@code false}
	 *
	 * @param terminateExecutorServiceOnClose if {@code true} - then {@linkplain ExecutorService} will be attempted to shutdown on {@linkplain DB#close()} method invocation
	 * @return a {@linkplain Builder} instance
	 */
	@Nonnull
	public Builder withTerminateExecutorServiceOnClose(boolean terminateExecutorServiceOnClose) {
	  this.terminateExecutorServiceOnClose = terminateExecutorServiceOnClose;
	  return this;
	}

	/**
	 * Configures a {@linkplain DB} instance with connection pool termination on close value provided<br/>
	 * Default is {@code true}
	 *
	 * @param terminateConnectionPoolOnClose if {@code true} - then underlying connection pool will be attempted to shut down on {@linkplain DB#close()} method invocation
	 * @return a {@linkplain Builder} instance
	 */
	@Nonnull
	public Builder withTerminateConnectionPoolOnClose(boolean terminateConnectionPoolOnClose) {
	  this.terminateConnectionPoolOnClose = terminateConnectionPoolOnClose;
	  return this;
	}

	/**
	 * Configures a {@linkplain DB} instance with connection manager instance provided<br/>
	 *
	 * @param connectionManager a connection manager instance
	 * @return a {@linkplain Builder} instance
	 * @throws NullPointerException if {@code connectionManager} is null
	 */
	@Nonnull
	public Builder withConnectionManager(ConnectionManager connectionManager) {
	  this.connectionManager = requireNonNull(connectionManager, "Connection manager must be provided");
	  return this;
	}

	/**
	 * Configures a {@linkplain DB} instance with an upper limit of obtained (by this {@linkplain DB} instance) connections count<br/>
	 * Default value is {@linkplain Runtime#availableProcessors()}
	 *
	 * @param count maximum connection to obtain (values less than {@code 1} are silently ignored)
	 * @return a {@linkplain Builder} instance
	 */
	@Nonnull
	public Builder withMaxConnections(int count) {
	  this.maxConnections = max(1, count);
	  return this;
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
	 * @param connectionProvider a function that returns a connection to database
	 * @return a new {@linkplain DB} instance. Never null
	 * @throws NullPointerException if {@code connectionProvider} is null
	 */
	@Nonnull
	public DB build(TrySupplier<Connection, SQLException> connectionProvider) {
	  requireNonNull(connectionProvider, "Connection provider function must be provided");
	  return new DB(
			  new ConcurrentHashMap<>(),
			  txIdProvider,
			  null == connectionManager ? new DefaultConnectionManager(connectionProvider, maxConnections, Duration.ofSeconds(5)) : connectionManager,
			  executorServiceSupplier,
			  terminateExecutorServiceOnClose,
			  terminateConnectionPoolOnClose
	  );
	}
  }

  /**
   * Creates a new builder instance
   *
   * @return a new {@code Builder} instance. Never null
   */
  @Nonnull
  public static Builder builder() {
	return new Builder();
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
  @Nonnull
  public static DB create(TrySupplier<Connection, SQLException> connectionProvider) {
	return DB.builder().build(connectionProvider);
  }

}
