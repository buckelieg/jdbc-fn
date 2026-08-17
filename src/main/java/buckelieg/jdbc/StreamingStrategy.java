/*
 * Copyright 2026- Anatoly Kutyakov
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

import buckelieg.fn.TryQuadConsumer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Locale;

import static java.sql.ResultSet.FETCH_FORWARD;

/**
 * Resolves the streaming configuration supported by the database behind an
 * actual JDBC connection. This works for connections obtained from either
 * {@code DriverManager} or a {@code DataSource}.
 */
enum StreamingStrategy {

  /** MySQL Connector/J row-by-row streaming configuration. */
  MYSQL("mysql", (connection, statement, fetchSize, streaming) -> {
	statement.setFetchDirection(FETCH_FORWARD);
	if (Select.Streaming.BUFFERED == streaming) return;
	statement.setFetchSize(Integer.MIN_VALUE);
  }),

  /** PostgreSQL cursor-based streaming configuration. */
  POSTGRESQL("postgresql", (connection, statement, fetchSize, streaming) -> {
	statement.setFetchDirection(FETCH_FORWARD);
	if (Select.Streaming.BUFFERED == streaming) return;
	if (connection.getAutoCommit()) connection.setAutoCommit(false);
	statement.setFetchSize(fetchSize);
  }),

  /** Oracle JDBC row-prefetch streaming configuration. */
  ORACLE("oracle", (connection, statement, fetchSize, streaming) -> {
	statement.setFetchDirection(FETCH_FORWARD);
	if (Select.Streaming.BUFFERED == streaming) return;
	statement.setFetchSize(fetchSize);
  }),

  /** Microsoft SQL Server adaptive response buffering configuration. */
  SQL_SERVER("sqlserver", (connection, statement, fetchSize, streaming) -> {
	statement.setFetchDirection(FETCH_FORWARD);
	if (Select.Streaming.BUFFERED == streaming) return;
	try {
	  Method method = statement.getClass().getMethod("setResponseBuffering", String.class);
	  method.invoke(statement, "adaptive");
	} catch (NoSuchMethodException e) {
	  throw new SQLException("Microsoft SQL Server JDBC statement does not expose adaptive buffering", e);
	} catch (IllegalAccessException e) {
	  throw new SQLException("Cannot enable Microsoft SQL Server adaptive buffering", e);
	} catch (InvocationTargetException e) {
	  Throwable cause = e.getCause();
	  if (cause instanceof SQLException) throw (SQLException) cause;
	  throw new SQLException("Cannot enable Microsoft SQL Server adaptive buffering", cause);
	}
	statement.setFetchSize(fetchSize);
  }),

  /** Apache Derby forward-only fetch configuration. */
  DERBY("derby", (connection, statement, fetchSize, streaming) -> {
	statement.setFetchDirection(FETCH_FORWARD);
	if (Select.Streaming.BUFFERED == streaming) return;
	statement.setFetchSize(fetchSize);
  }),

  /** Standards-based fallback for JDBC drivers without a verified streaming strategy. */
  GENERIC("", (connection, statement, fetchSize, streaming) -> {
	statement.setFetchDirection(FETCH_FORWARD);
	if (Select.Streaming.BUFFERED == streaming) return;
	if (Select.Streaming.REQUIRED == streaming) {
	  DatabaseMetaData metadata = connection.getMetaData();
	  String driver = null == metadata ? "unknown" : metadata.getDriverName();
	  throw new SQLFeatureNotSupportedException(
			  "Bounded-memory streaming is not verified for JDBC driver '"
					  + (null == driver ? "unknown" : driver)
					  + "'; use Select.streaming(PREFERRED) for standard JDBC fetch-size hints"
	  );
	}
	statement.setFetchSize(fetchSize);
  });

  private final String vendorMarker;
  private final TryQuadConsumer<Connection, Statement, Integer, Select.Streaming, SQLException> implementation;

  StreamingStrategy(
		  String vendorMarker,
		  TryQuadConsumer<Connection, Statement, Integer, Select.Streaming, SQLException> implementation) {
	this.vendorMarker = vendorMarker;
	this.implementation = implementation;
  }

  static StreamingStrategy resolve(Connection connection) throws SQLException {
	DatabaseMetaData metadata = connection.getMetaData();
	if (null == metadata) return GENERIC;

	StreamingStrategy strategy = resolve(metadata.getDatabaseProductName());
	if (GENERIC != strategy) return strategy;
	strategy = resolve(metadata.getDriverName());
	if (GENERIC != strategy) return strategy;
	return resolve(metadata.getURL());
  }

  private static StreamingStrategy resolve(String identity) {
	String normalized = null == identity
			? ""
			: identity.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
	for (StreamingStrategy candidate : values()) {
	  if (candidate != GENERIC && normalized.contains(candidate.vendorMarker)) return candidate;
	}
	return GENERIC;
  }

  void configure(Connection connection, Statement statement, Integer fetchSize, Select.Streaming streaming) throws SQLException {
	implementation.accept(connection, statement, fetchSize, streaming);
  }
}
