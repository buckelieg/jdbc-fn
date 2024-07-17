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

import buckelieg.jdbc.fn.TrySupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class DefaultConnectionManager implements ConnectionManager {

  private final TrySupplier<Connection, SQLException> connectionSupplier;

  private final int maxConnections;

  private final BlockingQueue<Connection> pool;

  private final List<Connection> obtainedConnections;

  private final AtomicInteger size = new AtomicInteger(0);

  private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

  private final AtomicLong activeConnections = new AtomicLong(0);

  private final Duration waitOnClose;

  DefaultConnectionManager(TrySupplier<Connection, SQLException> connectionSupplier, int maxConnections, Duration waitOnClose) {
	this.connectionSupplier = connectionSupplier;
	this.maxConnections = maxConnections;
	this.pool = new ArrayBlockingQueue<>(maxConnections);
	this.obtainedConnections = new CopyOnWriteArrayList<>();
	this.waitOnClose = waitOnClose;
  }

  @Nonnull
  @Override
  public Connection getConnection() throws SQLException {
	if (isShuttingDown.get()) throw new SQLException("Connection pool is shutting down");
	Connection connection;
	try {
	  if (size.get() < maxConnections) {
		size.incrementAndGet();
		connection = connectionSupplier.get();
		if (null == connection) throw new NullPointerException("Provided connection is null");
		if (obtainedConnections.contains(connection)) connection = pool.take();
		else obtainedConnections.add(connection);
	  } else connection = pool.take();
	} catch (InterruptedException e) {
	  Thread.currentThread().interrupt();
	  throw new SQLException(e);
	}
	connection.setAutoCommit(false);
	activeConnections.incrementAndGet();
	return connection;
  }

  @Override
  public void close(@Nullable Connection connection) throws SQLException {
	if (null == connection) return;
	connection.setAutoCommit(true);
	activeConnections.decrementAndGet();
	if (!pool.offer(connection)) throw new SQLException("Connection pool is full");
  }

  @Override
  public void close() throws SQLException {
	isShuttingDown.set(true);
	if(activeConnections.get() > 0) {
	  // gracefully closing pool waiting for configured time for existing transactions to complete
	  try {
		Thread.sleep(waitOnClose.toMillis());
	  } catch (InterruptedException e) {
		Thread.currentThread().interrupt();
	  }
	}
	pool.clear();
	SQLException exception = null;
	for (Connection connection : obtainedConnections) {
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

}
