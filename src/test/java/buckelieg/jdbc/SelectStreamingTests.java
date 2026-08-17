package buckelieg.jdbc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectStreamingTests {

  public interface SqlServerStatement extends Statement {
	void setResponseBuffering(String value) throws SQLException;
  }

  @Test
  void resolvesDedicatedStrategyForEverySupportedDriver() throws Exception {
    assertSame(StreamingStrategy.MYSQL, StreamingStrategy.resolve(connection("MySQL", "MySQL Connector/J", null, null)));
    assertSame(StreamingStrategy.POSTGRESQL, StreamingStrategy.resolve(connection("PostgreSQL", "PostgreSQL JDBC Driver", null, null)));
    assertSame(StreamingStrategy.ORACLE, StreamingStrategy.resolve(connection("Oracle", "Oracle JDBC Driver", null, null)));
    assertSame(StreamingStrategy.SQL_SERVER, StreamingStrategy.resolve(connection("Microsoft SQL Server", "Microsoft JDBC Driver", null, null)));
    assertSame(StreamingStrategy.DERBY, StreamingStrategy.resolve(connection("Apache Derby", "Derby Embedded JDBC Driver", null, null)));
    assertSame(StreamingStrategy.GENERIC, StreamingStrategy.resolve(connection("Unknown DB", "Unknown JDBC Driver", null, null)));
  }

  @Test
  void resolvesStrategyFromDriverMetadataWhenProductNameIsUnavailable() throws Exception {
	assertSame(StreamingStrategy.MYSQL, StreamingStrategy.resolve(connection("", "MySQL Connector/J", null, null)));
	assertSame(StreamingStrategy.POSTGRESQL, StreamingStrategy.resolve(connection(null, "PostgreSQL JDBC Driver", null, null)));
	assertSame(StreamingStrategy.SQL_SERVER, StreamingStrategy.resolve(connection("", "Microsoft JDBC Driver for SQL Server", null, null)));
  }

  @Test
  void resolvesStrategyFromConnectionUrlAsALastResort() throws Exception {
	assertSame(
			StreamingStrategy.ORACLE,
			StreamingStrategy.resolve(connection("", "", "jdbc:oracle:thin:@localhost:1521/test", null, null))
	);
  }

  @Test
  void mysqlUsesDocumentedRowByRowFetchSignal() {
	AtomicInteger fetchSize = new AtomicInteger();
	AtomicInteger executions = new AtomicInteger();
	ResultSetMetaData resultMetadata = metadata(0, "");
	PreparedStatement statement = statement(resultMetadata, executions, fetchSize);
	Connection connection = connection("MySQL", "MySQL Connector/J", statement, null);

	SelectQuery query = query(connection, "SELECT VALUE FROM TEST");
	assertEquals(0L, query.execute(reader -> 1).count());
	assertEquals(Integer.MIN_VALUE, fetchSize.get());
	assertEquals(1, executions.get());
  }

  @Test
  void configuresPostgreSqlCursorAndStandardFetchStrategies() throws Exception {
	AtomicBoolean autoCommit = new AtomicBoolean(true);
	AtomicInteger fetchDirection = new AtomicInteger();
	AtomicInteger fetchSize = new AtomicInteger();
	Connection connection = streamingConnection(autoCommit);
	Statement statement = streamingStatement(Statement.class, fetchDirection, fetchSize, new AtomicReference<>());

	StreamingStrategy.POSTGRESQL.configure(connection, statement, 128, Select.Streaming.REQUIRED);
	assertFalse(autoCommit.get());
	assertEquals(ResultSet.FETCH_FORWARD, fetchDirection.get());
	assertEquals(128, fetchSize.get());

	fetchSize.set(0);
	StreamingStrategy.ORACLE.configure(connection, statement, 64, Select.Streaming.REQUIRED);
	assertEquals(64, fetchSize.get());
	StreamingStrategy.DERBY.configure(connection, statement, 32, Select.Streaming.REQUIRED);
	assertEquals(32, fetchSize.get());
  }

  @Test
  void configuresSqlServerAdaptiveBufferingAndRejectsUnsupportedStatements() throws Exception {
	AtomicInteger fetchDirection = new AtomicInteger();
	AtomicInteger fetchSize = new AtomicInteger();
	AtomicReference<String> responseBuffering = new AtomicReference<>();
	Statement supported = streamingStatement(SqlServerStatement.class, fetchDirection, fetchSize, responseBuffering);

	StreamingStrategy.SQL_SERVER.configure(streamingConnection(new AtomicBoolean(false)), supported, 256, Select.Streaming.REQUIRED);
	assertEquals("adaptive", responseBuffering.get());
	assertEquals(256, fetchSize.get());

	Statement unsupported = streamingStatement(Statement.class, fetchDirection, fetchSize, responseBuffering);
	assertThrows(SQLException.class, () -> StreamingStrategy.SQL_SERVER.configure(
			streamingConnection(new AtomicBoolean(false)), unsupported, 256, Select.Streaming.REQUIRED
	));
  }

  @Test
  void genericStrategyRequiresExplicitFallbackAndBufferedModeAvoidsFetchHint() throws Exception {
	AtomicInteger fetchDirection = new AtomicInteger();
	AtomicInteger fetchSize = new AtomicInteger();
	Statement statement = streamingStatement(Statement.class, fetchDirection, fetchSize, new AtomicReference<>());
	Connection connection = connection("Unknown DB", "Unknown JDBC Driver", null, null);

	assertThrows(SQLFeatureNotSupportedException.class, () -> StreamingStrategy.GENERIC.configure(
			connection, statement, 40, Select.Streaming.REQUIRED
	));
	StreamingStrategy.GENERIC.configure(connection, statement, 40, Select.Streaming.PREFERRED);
	assertEquals(40, fetchSize.get());
	fetchSize.set(0);
	StreamingStrategy.GENERIC.configure(connection, statement, 40, Select.Streaming.BUFFERED);
	assertEquals(0, fetchSize.get());
	assertEquals(ResultSet.FETCH_FORWARD, fetchDirection.get());
  }

  @Test
  void requiredStreamingRejectsAnUnknownDriverBeforeExecution() {
	AtomicInteger executions = new AtomicInteger();
	PreparedStatement statement = statement(metadata(0, ""), executions, new AtomicInteger());
	Connection connection = connection("Unknown DB", "Unknown JDBC driver", statement, null);

	SelectQuery query = query(connection, "SELECT VALUE FROM TEST");
	assertThrows(SQLRuntimeException.class, () -> query.execute(reader -> 1).count());
	assertEquals(0, executions.get());
  }

  @Test
  void probeClosesItsResultBeforeSchemaMetadataAndWarmsCacheBeforeMapper() {
	AtomicInteger executions = new AtomicInteger();
	AtomicInteger primaryKeyLookups = new AtomicInteger();
	AtomicBoolean probeClosed = new AtomicBoolean();
	AtomicBoolean mapperStarted = new AtomicBoolean();
	AtomicReference<Boolean> outcome = new AtomicReference<>();
	ResultSetMetaData resultMetadata = metadata(1, "TEST");

	DatabaseMetaData databaseMetadata = (DatabaseMetaData) Proxy.newProxyInstance(
			getClass().getClassLoader(),
			new Class<?>[]{DatabaseMetaData.class},
			(proxy, method, args) -> {
			  switch (method.getName()) {
				case "getDatabaseProductName":
				  return "Apache Derby";
				case "getDriverName":
				  return "Derby Embedded JDBC Driver";
				case "getPrimaryKeys":
				  assertTrue(probeClosed.get(), "probe ResultSet must be closed before schema metadata lookup");
                  assertFalse(mapperStarted.get(), "schema metadata must be loaded before mapper invocation");
				  primaryKeyLookups.incrementAndGet();
				  return primaryKeys();
				case "getImportedKeys":
				  assertTrue(probeClosed.get(), "probe ResultSet must be closed before schema metadata lookup");
				  return emptyResultSet(null);
				default:
				  return defaultValue(method.getReturnType());
			  }
			}
	);

	PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
			getClass().getClassLoader(),
			new Class<?>[]{PreparedStatement.class},
			(proxy, method, args) -> {
			  switch (method.getName()) {
				case "getMetaData":
				  return null;
				case "executeQuery":
				  int execution = executions.incrementAndGet();
				  return execution == 1
						  ? probeResultSet(resultMetadata, probeClosed)
						  : singleRowResultSet(resultMetadata);
				case "isClosed":
				  return false;
				case "getMoreResults":
				  return false;
				default:
				  return defaultValue(method.getReturnType());
			  }
			}
	);

	Connection connection = connection("Apache Derby", "Derby Embedded JDBC Driver", statement, databaseMetadata);
	SelectQuery query = new SelectQuery(
			new ConcurrentHashMap<>(),
			() -> connection,
			(ignored, successful) -> outcome.set(successful),
			Executors.newSingleThreadExecutor(),
			"SELECT ID FROM TEST"
	);

	try {
	  assertEquals(Collections.singletonList(Boolean.TRUE), query.execute(reader -> {
		mapperStarted.set(true);
		return reader.meta().isPrimaryKey(1);
	  }).collect(java.util.stream.Collectors.toList()));
	  assertEquals(2, executions.get());
	  assertEquals(1, primaryKeyLookups.get());
	  assertEquals(Boolean.TRUE, outcome.get());
	} finally {
	  query.executorService.shutdownNow();
	}
  }

  private static SelectQuery query(Connection connection, String sql) {
	return new SelectQuery(
			new ConcurrentHashMap<>(),
			() -> connection,
			(ignored, successful) -> {},
			Executors.newSingleThreadExecutor(),
			sql
	);
  }

  private static Connection connection(
		  String product,
		  String driver,
		  PreparedStatement statement,
		  DatabaseMetaData suppliedMetadata) {
	return connection(product, driver, null, statement, suppliedMetadata);
  }

  private static Connection connection(
		  String product,
		  String driver,
		  String url,
		  PreparedStatement statement,
		  DatabaseMetaData suppliedMetadata) {
	DatabaseMetaData metadata = null == suppliedMetadata
			? (DatabaseMetaData) Proxy.newProxyInstance(
					SelectStreamingTests.class.getClassLoader(),
					new Class<?>[]{DatabaseMetaData.class},
					(proxy, method, args) -> {
					  if ("getDatabaseProductName".equals(method.getName())) return product;
					  if ("getDriverName".equals(method.getName())) return driver;
					  if ("getURL".equals(method.getName())) return url;
					  return defaultValue(method.getReturnType());
					}
			)
			: suppliedMetadata;
	return (Connection) Proxy.newProxyInstance(
			SelectStreamingTests.class.getClassLoader(),
			new Class<?>[]{Connection.class},
			(proxy, method, args) -> {
			  switch (method.getName()) {
				case "prepareStatement":
				  return statement;
				case "getMetaData":
				  return metadata;
				case "getAutoCommit":
				  return false;
				case "hashCode":
				  return System.identityHashCode(proxy);
				case "equals":
				  return proxy == args[0];
				default:
				  return defaultValue(method.getReturnType());
			  }
			}
	);
  }

  private static PreparedStatement statement(
		  ResultSetMetaData metadata,
		  AtomicInteger executions,
		  AtomicInteger fetchSize) {
	return (PreparedStatement) Proxy.newProxyInstance(
			SelectStreamingTests.class.getClassLoader(),
			new Class<?>[]{PreparedStatement.class},
			(proxy, method, args) -> {
			  switch (method.getName()) {
				case "getMetaData":
				  return metadata;
				case "setFetchSize":
				  fetchSize.set((Integer) args[0]);
				  return null;
				case "executeQuery":
				  executions.incrementAndGet();
				  return emptyResultSet(metadata);
				case "isClosed":
				  return false;
				case "getMoreResults":
				  return false;
				default:
				  return defaultValue(method.getReturnType());
			  }
			}
	);
  }

  @SuppressWarnings("unchecked")
  private static <T extends Statement> T streamingStatement(
		  Class<T> type,
		  AtomicInteger fetchDirection,
		  AtomicInteger fetchSize,
		  AtomicReference<String> responseBuffering) {
	return (T) Proxy.newProxyInstance(
			SelectStreamingTests.class.getClassLoader(),
			new Class<?>[]{type},
			(proxy, method, args) -> {
			  switch (method.getName()) {
				case "setFetchDirection":
				  fetchDirection.set((Integer) args[0]);
				  return null;
				case "setFetchSize":
				  fetchSize.set((Integer) args[0]);
				  return null;
				case "setResponseBuffering":
				  responseBuffering.set((String) args[0]);
				  return null;
				default:
				  return defaultValue(method.getReturnType());
			  }
			}
	);
  }

  private static Connection streamingConnection(AtomicBoolean autoCommit) {
	return (Connection) Proxy.newProxyInstance(
			SelectStreamingTests.class.getClassLoader(),
			new Class<?>[]{Connection.class},
			(proxy, method, args) -> {
			  if ("getAutoCommit".equals(method.getName())) return autoCommit.get();
			  if ("setAutoCommit".equals(method.getName())) {
				autoCommit.set((Boolean) args[0]);
				return null;
			  }
			  return defaultValue(method.getReturnType());
			}
	);
  }

  private static ResultSetMetaData metadata(int columns, String table) {
	return (ResultSetMetaData) Proxy.newProxyInstance(
			SelectStreamingTests.class.getClassLoader(),
			new Class<?>[]{ResultSetMetaData.class},
			(proxy, method, args) -> {
			  switch (method.getName()) {
				case "getColumnCount":
				  return columns;
				case "getColumnType":
				  return Types.INTEGER;
				case "getColumnClassName":
				  return Integer.class.getName();
				case "getColumnName":
				case "getColumnLabel":
				  return "ID";
				case "getTableName":
				  return table;
				case "getCatalogName":
				case "getSchemaName":
				  return "";
				case "isNullable":
				  return ResultSetMetaData.columnNoNulls;
				default:
				  return defaultValue(method.getReturnType());
			  }
			}
	);
  }

  private static ResultSet probeResultSet(ResultSetMetaData metadata, AtomicBoolean closed) {
	return (ResultSet) Proxy.newProxyInstance(
			SelectStreamingTests.class.getClassLoader(),
			new Class<?>[]{ResultSet.class},
			(proxy, method, args) -> {
			  if ("getMetaData".equals(method.getName())) return metadata;
			  if ("close".equals(method.getName())) {
				closed.set(true);
				return null;
			  }
			  return defaultValue(method.getReturnType());
			}
	);
  }

  private static ResultSet singleRowResultSet(ResultSetMetaData metadata) {
	AtomicInteger cursor = new AtomicInteger();
	return (ResultSet) Proxy.newProxyInstance(
			SelectStreamingTests.class.getClassLoader(),
			new Class<?>[]{ResultSet.class},
			(proxy, method, args) -> {
			  if ("getMetaData".equals(method.getName())) return metadata;
			  if ("next".equals(method.getName())) return cursor.incrementAndGet() == 1;
			  return defaultValue(method.getReturnType());
			}
	);
  }

  private static ResultSet primaryKeys() {
	AtomicInteger cursor = new AtomicInteger();
	return (ResultSet) Proxy.newProxyInstance(
			SelectStreamingTests.class.getClassLoader(),
			new Class<?>[]{ResultSet.class},
			(proxy, method, args) -> {
			  if ("next".equals(method.getName())) return cursor.incrementAndGet() == 1;
			  if ("getString".equals(method.getName())) return "ID";
			  return defaultValue(method.getReturnType());
			}
	);
  }

  private static ResultSet emptyResultSet(ResultSetMetaData metadata) {
	return (ResultSet) Proxy.newProxyInstance(
			SelectStreamingTests.class.getClassLoader(),
			new Class<?>[]{ResultSet.class},
			(proxy, method, args) -> {
			  if ("getMetaData".equals(method.getName())) return metadata;
			  if ("next".equals(method.getName())) return false;
			  return defaultValue(method.getReturnType());
			}
	);
  }

  private static Object defaultValue(Class<?> type) {
	if (!type.isPrimitive()) return null;
	if (boolean.class == type) return false;
	if (byte.class == type) return (byte) 0;
	if (short.class == type) return (short) 0;
	if (int.class == type) return 0;
	if (long.class == type) return 0L;
	if (float.class == type) return 0F;
	if (double.class == type) return 0D;
	if (char.class == type) return '\0';
	return null;
  }
}
