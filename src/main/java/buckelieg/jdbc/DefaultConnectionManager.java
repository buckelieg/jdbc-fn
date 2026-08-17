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

import static java.lang.String.format;

final class DefaultConnectionManager implements ConnectionManager {

  private static final long POOL_WAIT_TIMEOUT_MILLIS = 100L;

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
	while (true) {
	  Connection connection;
	  try {
		connection = acquireConnection();
	  } catch (InterruptedException e) {
		Thread.currentThread().interrupt();
		throw new SQLException(e);
	  }
	  if (isShuttingDown.get()) {
		discard(connection);
		throw new SQLException("Connection pool is shutting down");
	  }

	  try {
		if (connection.isClosed()) {
		  discard(connection);
		  continue;
		}

		ConnectionMetadata metadata = obtainedConnections.get(connection);
		if (null == metadata) {
		  discard(connection);
		  continue;
		}
		if (!metadata.isBusy.compareAndSet(false, true)) {
		  if (!pool.offer(connection)) throw new SQLException("Connection pool is full");
		  continue;
		}

		connection.setAutoCommit(false);
		activeSessions.incrementAndGet();
		metadata.lastTimeUsed.set(System.currentTimeMillis());
		return connection;
	  } catch (SQLException e) {
		discardAfterFailure(connection, e);
		throw e;
	  } catch (RuntimeException e) {
		discardAfterFailure(connection, e);
		throw e;
	  }
	}
  }

  private Connection acquireConnection() throws SQLException, InterruptedException {
	while (!isShuttingDown.get()) {
	  Connection connection = pool.poll();
	  if (null != connection) return connection;

	  if (reserveConnectionSlot()) {
		connection = createConnection();
		if (null != connection) return connection;

		// A supplier may return a connection which is already leased by this
		// manager. Wait for it to become idle instead of repeatedly invoking
		// the supplier in a tight loop.
		connection = pool.poll(POOL_WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		if (null != connection) return connection;
		continue;
	  }

	  connection = pool.poll(POOL_WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
	  if (null != connection) return connection;
	}
	throw new SQLException("Connection pool is shutting down");
  }

  private boolean reserveConnectionSlot() {
	int current;
	do {
	  current = size.get();
	  if (current >= maxConnections) return false;
	} while (!size.compareAndSet(current, current + 1));
	return true;
  }

  private Connection createConnection() throws SQLException {
	Connection connection = null;
	boolean registered = false;
	try {
	  connection = connectionSupplier.get();
	  if (null == connection) throw new SQLException("Provided connection is null");

	  ConnectionMetadata metadata = ConnectionMetadata.of(connection);
	  if (null != obtainedConnections.putIfAbsent(connection, metadata)) return null;
	  registered = true;
	  return connection;
	} catch (Throwable failure) {
	  if (null != connection && !registered) {
		try {
		  connection.close();
		} catch (SQLException | RuntimeException closeFailure) {
		  failure.addSuppressed(closeFailure);
		}
	  }
	  if (failure instanceof SQLException) throw (SQLException) failure;
	  if (failure instanceof Error) throw (Error) failure;
	  throw new SQLException(failure);
	} finally {
	  if (!registered) size.decrementAndGet();
	}
  }

  private void discard(Connection connection) throws SQLException {
	if (null != connection) pool.remove(connection);
	if (null != connection && null != obtainedConnections.remove(connection)) size.decrementAndGet();
	if (null != connection) connection.close();
  }

  private void discardAfterFailure(Connection connection, Throwable failure) {
	try {
	  discard(connection);
	} catch (SQLException | RuntimeException closeFailure) {
	  failure.addSuppressed(closeFailure);
	}
  }

  @Override
  public void close(Connection connection) throws SQLException {
	release(connection, null);
  }

  @Override
  public void close(Connection connection, boolean transactionSucceeded) throws SQLException {
	release(connection, transactionSucceeded);
  }

  private void release(Connection connection, Boolean transactionSucceeded) throws SQLException {
	if (null == connection) return;
	ConnectionMetadata metadata = obtainedConnections.get(connection);
	boolean leaseReleased = false;
	try {
	  if (null == metadata || connection.isClosed()) {
		discard(connection);
		return;
	  }

	  // A null outcome means that the caller (an explicit transaction) has
	  // already made its commit/rollback decision. Roll back defensively so
	  // returning a connection can never commit unfinished work implicitly.
	  if (Boolean.TRUE.equals(transactionSucceeded)) connection.commit();
	  else connection.rollback();

	  if (isShuttingDown.get()) {
		discard(connection);
		return;
	  }

	  connection.clearWarnings();
	  connection.setHoldability(metadata.holdability);
	  connection.setReadOnly(metadata.readOnly);
	  connection.setTransactionIsolation(metadata.isolationLevel);
	  connection.setAutoCommit(metadata.autoCommit);

	  leaseReleased = metadata.isBusy.compareAndSet(true, false);
	  if (!leaseReleased) throw new SQLException("Connection is not leased");
	  if (!pool.offer(connection)) throw new SQLException("Connection pool is full");
	  metadata.lastTimeUsed.set(System.currentTimeMillis());
	} catch (SQLException e) {
	  discardAfterFailure(connection, e);
	  throw e;
	} catch (RuntimeException e) {
	  discardAfterFailure(connection, e);
	  throw e;
	} finally {
	  if (null != metadata && (leaseReleased || metadata.isBusy.compareAndSet(true, false))) {
		activeSessions.decrementAndGet();
		mutex.updateAndGet(latch -> {
		  if (null != latch) latch.countDown();
		  return latch;
		});
	  }
	}
  }

  @Override
  public void close() throws SQLException {
	isShuttingDown.set(true);
	if (null != keepAlive) keepAlive.cancel(true);
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
	executorService.shutdownNow();
	if (null != exception) throw exception;
  }

  private void executeQuery(String query) {
	int connectionsToCheck = pool.size();
	for (int i = 0; i < connectionsToCheck && !isShuttingDown.get(); i++) {
	  Connection connection = pool.poll();
	  if (null == connection) return;

	  ConnectionMetadata metadata = obtainedConnections.get(connection);
	  boolean claimed = false;
	  boolean healthy = true;
	  try {
		if (null == metadata || connection.isClosed()) {
		  healthy = false;
		  continue;
		}
		if ((System.currentTimeMillis() - metadata.lastTimeUsed.get()) <= TimeUnit.SECONDS.toMillis(5)) continue;
		if (!metadata.isBusy.compareAndSet(false, true)) continue;
		claimed = true;

		try (Statement statement = connection.createStatement()) {
		  statement.execute(query);
		  metadata.lastTimeUsed.set(System.currentTimeMillis());
		}
	  } catch (SQLException | RuntimeException e) {
		healthy = false;
	  } finally {
		if (claimed) metadata.isBusy.compareAndSet(true, false);
		if (healthy && !isShuttingDown.get()) {
		  if (!pool.offer(connection)) discardQuietly(connection);
		} else discardQuietly(connection);
	  }
	}
  }

  private void discardQuietly(Connection connection) {
	try {
	  discard(connection);
	} catch (SQLException | RuntimeException ignored) {
	  // A failed liveness check must not stop future scheduled checks.
	}
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
