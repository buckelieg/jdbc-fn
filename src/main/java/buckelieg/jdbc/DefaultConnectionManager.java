/*
 * Copyright 2024- Anatoly Kutyakov
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

import buckelieg.fn.TryPredicate;
import buckelieg.fn.TrySupplier;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static buckelieg.jdbc.Utils.newSQLRuntimeException;
import static java.lang.String.format;

final class DefaultConnectionManager implements ConnectionManager {

  private final TrySupplier<Connection, SQLException> connectionSupplier;

  private final int maxConnections;

  private final BlockingQueue<Connection> pool;

  private final Map<Connection, ConnectionMetadata> obtainedConnections = new ConcurrentHashMap<>();

  private final AtomicInteger size = new AtomicInteger(0);

  private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

  private final AtomicInteger activeSessions = new AtomicInteger(0);

  private final Duration waitOnClose;

  private final Duration waitOnIdle;

  private final AtomicReference<CountDownLatch> mutex = new AtomicReference<>();

  private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

  private final ScheduledFuture<?> keepAlive;

  DefaultConnectionManager(
		  TrySupplier<Connection, SQLException> connectionSupplier,
		  int maxConnections,
		  Duration waitOnClose,
		  String keepAliveQuery,
		  Duration waitOnIdle
  ) {
	this.connectionSupplier = connectionSupplier;
	this.maxConnections = maxConnections;
	this.pool = new ArrayBlockingQueue<>(maxConnections);
	this.waitOnClose = waitOnClose;
	this.waitOnIdle = waitOnIdle;
	this.keepAlive = Optional.ofNullable(keepAliveQuery)
			.map(String::trim)
			.filter(TryPredicate.not(String::isEmpty).toPredicate())
			.filter(Utils::isSingle).filter(Utils::isAnonymous)
			.filter(TryPredicate.not(Utils::isProcedure).toPredicate())
			.map(query -> executorService.scheduleAtFixedRate(() -> executeQuery(query), waitOnIdle.toMillis(), waitOnIdle.toMillis(), TimeUnit.MILLISECONDS))
			.orElse(null);
  }

  @Override
  public Connection getConnection() throws SQLException {
	if (isShuttingDown.get()) throw new SQLException("Connection pool is shutting down");
	Connection connection;
	try {
	  if (size.get() < maxConnections) {
		size.incrementAndGet();
		connection = connectionSupplier.get();
		if (null == connection) throw new NullPointerException("Provided connection is null");
		if (obtainedConnections.containsKey(connection)) connection = pool.take();
		else obtainedConnections.put(connection, ConnectionMetadata.of(connection));
	  } else connection = pool.take();
	  if (connection.isClosed()) {
		close(connection);
		connection = getConnection();
	  }
	  if (obtainedConnections.get(connection).isBusy.get() && pool.offer(connection)) connection = getConnection();
	} catch (InterruptedException e) {
	  Thread.currentThread().interrupt();
	  throw new SQLException(e);
	}
	connection.setAutoCommit(false);
	activeSessions.incrementAndGet();
	ConnectionMetadata metadata = obtainedConnections.get(connection);
	metadata.lastTimeUsed.set(System.currentTimeMillis());
	metadata.isBusy.compareAndSet(false, true);
	return connection;
  }

  @Override
  public void close(Connection connection) throws SQLException {
	if (null == connection) return;
	if (connection.isClosed()) {
	  obtainedConnections.remove(connection);
	  size.decrementAndGet();
	} else {
	  activeSessions.decrementAndGet();
	  if (isShuttingDown.get()) {
		mutex.updateAndGet(latch -> {
		  if (null != latch) latch.countDown();
		  return latch;
		});
		connection.close();
		return;
	  }
	  ConnectionMetadata metadata = obtainedConnections.get(connection);
	  connection.setAutoCommit(true);
	  connection.clearWarnings();
	  connection.setHoldability(metadata.holdability);
	  connection.setReadOnly(metadata.readOnly);
	  connection.setTransactionIsolation(metadata.isolationLevel);
	  if (pool.offer(connection)) {
		metadata.lastTimeUsed.set(System.currentTimeMillis());
		metadata.isBusy.compareAndSet(true, false);
	  } else throw new SQLException("Connection pool is full");
	}
  }

  @Override
  public void close() throws SQLException {
	if (null != keepAlive) keepAlive.cancel(true);
	isShuttingDown.set(true);
	SQLException exception = null;
	if (null != waitOnClose && activeSessions.get() > 0) { // gracefully closing pool waiting for configured time for existing transactions to complete
	  mutex.updateAndGet(mutex -> null == mutex ? new CountDownLatch(activeSessions.get()) : mutex);
	  if (!waitFor()) exception = new SQLException(format("Forcibly shutting down with unclosed sessions number of %s", activeSessions.get()));
	}
	pool.clear();
	for (Connection connection : obtainedConnections.keySet()) {
	  try {
		connection.close();
	  } catch (SQLException e) {
		if (null == exception) exception = new SQLException(e);
		else exception.setNextException(e);
	  }
	}
	obtainedConnections.clear();
	if (null != exception) throw exception;
  }

  private void executeQuery(String query) {
	if (isShuttingDown.get()) return;
	long now = System.currentTimeMillis();
	obtainedConnections.entrySet().stream()
			.filter(entry -> (now - entry.getValue().lastTimeUsed.get()) > TimeUnit.SECONDS.toMillis(5))
			.filter(entry -> !entry.getValue().isBusy.get())
			.forEach(entry -> {
			  entry.getValue().isBusy.compareAndSet(false, true);
			  Connection connection = entry.getKey();
			  ConnectionMetadata metadata = entry.getValue();
			  try (Statement statement = connection.createStatement()) {
				connection.setAutoCommit(false);
				statement.execute(query);
				connection.rollback();
				connection.setAutoCommit(true);
				metadata.lastTimeUsed.set(now);
				metadata.isBusy.compareAndSet(true, false);
			  } catch (SQLException e) {
				throw newSQLRuntimeException(e);
			  }
			});
  }

  private boolean waitFor() {
	try {
	  return mutex.get().await(waitOnClose.toMillis(), TimeUnit.MILLISECONDS);
	} catch (InterruptedException e) {
	  Thread.currentThread().interrupt();
	  return mutex.get().getCount() == 0;
	}
  }

}
