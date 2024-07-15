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

import buckelieg.jdbc.fn.TryConsumer;
import buckelieg.jdbc.fn.TryFunction;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;
import org.opentest4j.AssertionFailedError;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static buckelieg.jdbc.Utils.*;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;


// TODO more test suites for other RDBMS
public class DBTestSuite {

  private static final Logger log = LogManager.getLogger(DBTestSuite.class);
  private static Connection conn;
  private static DB db;
  private static DataSource ds;

  private static final AtomicInteger cursor = new AtomicInteger();

  private static final PrimitiveIterator.OfInt sequence = IntStream.generate(() -> cursor.getAndAdd(1)).iterator();

  @BeforeAll
  public static void init() throws Exception {
	Files.walkFileTree(Paths.get("test"), new SimpleFileVisitor<Path>() {
	  @Override
	  public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
		Files.delete(path);
		return FileVisitResult.CONTINUE;
	  }

	  @Override
	  public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
		if (exc != null) {
		  throw exc;
		}
		Files.delete(dir);
		return FileVisitResult.CONTINUE;
	  }
	});
	Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
	conn = DriverManager.getConnection("jdbc:derby:memory:test;create=true");
	EmbeddedDataSource ds = new EmbeddedDataSource();
	ds.setDatabaseName("test");
	ds.setCreateDatabase("create");
	DBTestSuite.ds = ds;
	conn = ds.getConnection();
	conn.createStatement().execute("CREATE TABLE TEST(id int PRIMARY KEY GENERATED ALWAYS AS IDENTITY, name VARCHAR(255) NOT NULL)");
	conn.createStatement().execute("CREATE TABLE TEST1(id int PRIMARY KEY GENERATED ALWAYS AS IDENTITY, name VARCHAR(255) NOT NULL)");
	conn.createStatement().execute("CREATE PROCEDURE CREATETESTROW1(name_to_add VARCHAR(255)) DYNAMIC RESULT SETS 2 LANGUAGE JAVA EXTERNAL NAME 'buckelieg.jdbc.DerbyStoredProcedures.createTestRow' PARAMETER STYLE JAVA");
	conn.createStatement().execute("CREATE PROCEDURE CREATETESTROW2(name_to_add VARCHAR(255)) LANGUAGE JAVA EXTERNAL NAME 'buckelieg.jdbc.DerbyStoredProcedures.testProcedure' PARAMETER STYLE JAVA");
	conn.createStatement().execute("CREATE PROCEDURE GETNAMEBYID(name_id INTEGER, OUT name_name VARCHAR(255)) LANGUAGE JAVA EXTERNAL NAME 'buckelieg.jdbc.DerbyStoredProcedures.testProcedureWithResults' PARAMETER STYLE JAVA");
	conn.createStatement().execute("CREATE PROCEDURE GETALLNAMES() DYNAMIC RESULT SETS 1 LANGUAGE JAVA EXTERNAL NAME 'buckelieg.jdbc.DerbyStoredProcedures.testNoArgProcedure' PARAMETER STYLE JAVA");
	conn.createStatement().execute("CREATE PROCEDURE ECHO(row_id INTEGER) LANGUAGE JAVA EXTERNAL NAME 'buckelieg.jdbc.DerbyStoredProcedures.echoProcedure' PARAMETER STYLE JAVA");
	conn.createStatement().execute("CREATE PROCEDURE P_GETROWBYID(id INTEGER) DYNAMIC RESULT SETS 1 LANGUAGE JAVA EXTERNAL NAME 'buckelieg.jdbc.DerbyStoredProcedures.testProcedureGetRowById' PARAMETER STYLE JAVA");
	conn.createStatement().execute("CREATE FUNCTION GETALLROWS() RETURNS TABLE (id INTEGER, name VARCHAR(255)) PARAMETER STYLE DERBY_JDBC_RESULT_SET READS SQL DATA LANGUAGE JAVA EXTERNAL NAME 'buckelieg.jdbc.DerbyStoredProcedures.testProcedureGetAllRows'");
	conn.createStatement().execute("CREATE FUNCTION GETROWBYID(id INTEGER) RETURNS TABLE (id INTEGER, name VARCHAR(255)) PARAMETER STYLE DERBY_JDBC_RESULT_SET READS SQL DATA LANGUAGE JAVA EXTERNAL NAME 'buckelieg.jdbc.DerbyStoredProcedures.testProcedureGetRowById'");
//        db = new DB(() -> conn);
//        db = new DB(conn);
//        db = DB.create(ds::getConnection);
	db = DB.builder()
			.withTransactionIdProvider(() -> "" + sequence.nextInt())
			.build(ds::getConnection);
  }

  @AfterAll
  public static void destroy() throws Exception {
	conn.createStatement().execute("DROP TABLE TEST");
	conn.createStatement().execute("DROP TABLE TEST1");
	conn.createStatement().execute("DROP PROCEDURE CREATETESTROW1");
	conn.createStatement().execute("DROP PROCEDURE CREATETESTROW2");
	conn.createStatement().execute("DROP PROCEDURE GETNAMEBYID");
	conn.createStatement().execute("DROP PROCEDURE GETALLNAMES");
	conn.createStatement().execute("DROP PROCEDURE ECHO");
	conn.createStatement().execute("DROP PROCEDURE P_GETROWBYID");
	conn.createStatement().execute("DROP FUNCTION GETALLROWS");
	conn.createStatement().execute("DROP FUNCTION GETROWBYID");
//        conn.close();
	db.close();
  }

  @BeforeEach
  public void reset() throws Exception {
	boolean autoCommit = conn.getAutoCommit();
	conn.setAutoCommit(false);
	conn.createStatement().executeUpdate("TRUNCATE TABLE TEST");
	conn.createStatement().executeUpdate("TRUNCATE TABLE TEST1");
	conn.createStatement().executeUpdate("ALTER TABLE TEST ALTER COLUMN ID RESTART WITH 1");
	conn.createStatement().executeUpdate("ALTER TABLE TEST1 ALTER COLUMN ID RESTART WITH 1");
	PreparedStatement ps = conn.prepareStatement("INSERT INTO TEST(name) VALUES(?)");
	PreparedStatement ps1 = conn.prepareStatement("INSERT INTO TEST1(name) VALUES(?)");
	for (int i = 0; i < 10; i++) {
	  ps.setString(1, "name_" + (i + 1));
	  ps1.setString(1, "name_" + (i + 1));
	  ps1.execute();
	  ps.execute();
	}
	conn.commit();
	conn.setAutoCommit(autoCommit);
  }

  @Test
  public void testMeta() throws Exception {
	PreparedStatement pst = conn.prepareStatement("SELECT t1.name AS \"name1\", t2.name AS \"name2\" FROM test t1 JOIN test1 t2 ON t1.id = t2.id");
	ResultSetMetaData meta = pst.getMetaData();
	int columnCount = meta.getColumnCount();
	for (int col = 1; col <= columnCount; col++) {
	  log.info("{}.{}:{}", meta.getTableName(col), meta.getColumnLabel(col), meta.getColumnClassName(col));
	}
	pst.close();
	db.select("SELECT * FROM TEST UNION ALL SELECT * FROM TEST1").single(rs -> rs.meta().names().stream()).orElse(Stream.empty()).forEach(log::info);
  }

  @Test
  public void testResultSet() throws Exception {
	try (ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM TEST")) {
	  int rows = 0;
	  while (rs.next()) {
		rows++;
	  }
	  assertEquals(10, rows);
	}
  }

  @Test
  public void testFetchSize() {
	assertEquals(10, db.select("SELECT * FROM TEST").fetchSize(1).execute().count());
  }

  @Test
  public void testMaxRows() {
	assertEquals(1, db.select("select * from test").maxRows(1).execute().count());
	assertEquals(1, db.select("select * from test").maxRows(1L).execute().count());
	assertEquals(2, db.select("select * from test").maxRows(1).maxRows(2L).execute().count());
	assertEquals(2, db.select("select * from test").maxRows(1L).maxRows(2).execute().count());
  }

  @Test
  public void testSelect() {
	assertEquals(2, db.select("SELECT * FROM TEST WHERE ID IN (?, ?)", 1, 2).execute().count());
	assertEquals(2, db.select("SELECT * FROM TEST WHERE ID IN (?, ?)", 1, 2).execute().count());
  }

  @Test
  public void testSelectSingle() {
	log.info(db.select("SELECT ID FROM TEST WHERE ID IN (1, 2, 3)").single());
  }

  @Test
  public void testSelectNoResults() {
	assertEquals(0, db.select("SELECT * FROM TEST WHERE ID = 1238").execute().count());
  }

  @Test
  public void testSelectNamed() {
	assertEquals(4, db.select("SELECT * FROM TEST WHERE 1=1 AND ID IN (:ID) OR NAME=:name OR NAME=:NAME", new HashMap<String, Object>() {{
	  put("ID", new Object[]{1, 2});
	  put("name", "name_5");
	  put("NAME", "name_6");
	}}).execute(rs -> entry(rs.getInt(1), rs.getString(2))).count());
  }

  @Test
  public void testSelectNoParams() {
	assertEquals(10, db.select("SELECT COUNT(*) FROM TEST").single(rs -> rs.getInt(1)).orElse(0).intValue());
  }

  @Test
  public void testSelectForEachSingle() {
//        assertEquals(1, db.select("SELECT * FROM TEST WHERE ID=1").execute().count());
	log.info(db.select("SELECT * FROM TEST WHERE ID=1").execute().count());
	db.select("SELECT * FROM TEST WHERE ID=1").execute(rs -> rs.getInt(1)).forEach(log::info);
	db.select("SELECT COUNT(*) FROM TEST").execute(rs -> rs.getInt(1)).forEach(log::info);
  }

  @Test
  public void testSelectAllFieldsWithDefaultMapper() {
	assertEquals(2, db.select("SELECT * FROM TEST WHERE ID=?", 1).execute().collect(toList()).get(0).size());
  }

  @Test
  public void testUpdateNoParams() {
	assertEquals(10L, db.update("DELETE FROM TEST").execute().longValue());
  }

  @Test
  public void testInsert() {
	assertEquals(1L, db.update("INSERT INTO TEST(name) VALUES(?)", "New_Name").execute().longValue());
  }

  @Test
  public void testInsertNamed() {
	assertEquals(1L, db.update("INSERT INTO TEST(name) VALUES(:name)", entry("name", "New_Name")).execute().longValue());
	assertEquals(11L, db.select("SELECT COUNT(*) FROM TEST").single((rs) -> rs.getLong(1)).orElse(-1L).longValue());
  }

  @Test
  public void testUpdate() {
	assertEquals(1L, db.update("UPDATE TEST SET NAME=? WHERE NAME=?", "new_name_2", "name_2").execute().longValue());
	assertEquals(1L, db.select("SELECT COUNT(*) FROM TEST WHERE name=?", "new_name_2").single((rs) -> rs.getLong(1)).orElse(-1L).longValue());
  }

  @Test
  public void testUpdateNamed() {
	assertEquals(1L, db.update("UPDATE TEST SET NAME=:name WHERE NAME=:new_name", entry("name", "new_name_2"), entry("new_name", "name_2")).execute().longValue());
	assertEquals(1L, db.select("SELECT COUNT(*) FROM TEST WHERE name=?", "new_name_2").single((rs) -> rs.getLong(1)).orElse(-1L).longValue());
  }

  @Test
  public void testUpdateBatch() {
	assertEquals(2L, db.update("INSERT INTO TEST(name) VALUES(?)", new Object[][]{{"name1"}, {"name2"}}).batch(2).execute().longValue());
  }

  @Test
  public void testUpdateBatchNamed() {
	Map<String, String> params1 = new HashMap<String, String>() {{
	  put("names", "name1");
	}};
	Map<String, String> params2 = new HashMap<String, String>() {{
	  put("names", "name2");
	}};
	assertEquals(2L, db.update("INSERT INTO TEST(name) VALUES(:names)", params1, params2).execute().longValue());
  }

  @Test
  public void testUpdateBatchBatch() {
	assertEquals(2L, db.update("INSERT INTO TEST(name) VALUES(?)", new Object[][]{{"name1"}, {"name2"}}).batch(2).execute().longValue());
  }

  @Test
  public void testLargeUpdate() {
	assertEquals(1L, db.update("INSERT INTO TEST(name) VALUES(?)", "largeupdatenametest").large(true).execute().longValue());
  }

  @Test
  public void testDelete() {
	assertEquals(1L, db.update("DELETE FROM TEST WHERE name=?", "name_2").execute().longValue());
	assertEquals(9L, db.select("SELECT COUNT(*) FROM TEST").single(rs -> rs.getLong(1)).orElse(-1L).longValue());
  }

  @Test
  public void testDeleteNamed() throws Exception {
	assertEquals(1L, db.update("DELETE FROM TEST WHERE name=:name", entry("name", "name_2")).execute().longValue());
	Thread.sleep(1000);
	assertEquals(9L, db.select("SELECT COUNT(*) FROM TEST").single(rs -> rs.getLong(1)).orElse(-1L).longValue());
  }

  @Test
  public void testSetTransactionIsolationLevel() {
	assertEquals(2L, db.update("DELETE FROM TEST WHERE id=?", new Object[][]{{1}, {2}}).execute().longValue());
	assertEquals(8L, db.select("SELECT COUNT(*) FROM TEST").single(rs -> rs.getLong(1)).orElse(-1L).longValue());
  }

  @Test
  public void testDuplicatedNamedParameters() {
	assertThrows(IllegalStateException.class, () -> db.select("SELECT * FROM TEST WHERE 1=1 AND (NAME IN (:names) OR NAME=:names)", entry("names", "name_1"), entry("names", "name_2")));
  }

  @Test
  public void testSameNamedParameter() { // TODO derby bug?
	assertThrows(SQLRuntimeException.class, () -> db.select("SELECT * FROM TEST WHERE 1=1 AND (ID = (CAST ? AS NUMBER)/* OR ID = (CAST :p2 AS NUMBER)*/)", 1, entry("p2", 1)).print(log::info).execute().count());
  }

  @Test
  public void testVoidStoredProcedure() {
	db.procedure("{call CREATETESTROW2(?)}", "new_name").call();
	assertEquals(11L, db.select("SELECT COUNT(*) FROM TEST").single(rs -> rs.getLong(1)).orElse(-1L).longValue());
  }

  @Test
  public void testStoredProcedureNonEmptyResult() {
	db.procedure("{call CREATETESTROW1(?)}", "new_name").call();
  }

  @Test
  public void testTableStoredFunction() {
	assertEquals(10, db.select("SELECT s.* FROM TABLE(GETALLROWS()) s").execute().peek(log::info).count());
  }

  @Test
  public void testTableStoredFunctionWithInParameter() {
	assertEquals(1, db.select("SELECT s.* FROM TABLE(GETROWBYID(?)) s", 1).execute().peek(log::info).count());
	assertEquals(1, db.select("SELECT s.* FROM TABLE(GETROWBYID(:id)) s", entry(":id", 1)).execute().peek(log::info).count());
  }

  @Test
  public void testResultSetStoredProcedure() {
	assertEquals(13, db.procedure("{call CREATETESTROW1(?)}", "new_name").execute().peek(log::info).count());
  }

  @Test
  public void testResultSetWithResultsStoredProcedure() {
	List<String> name = new ArrayList<>(1);
	assertEquals(0, db.procedure("call GETNAMEBYID(?, ?)", P.in(1), P.out(JDBCType.VARCHAR)).call(cs -> cs.getString(2), name::add).execute().count());
	assertEquals("name_1", name.get(0));
  }

  @Test
  public void testInvalidProcedureCall() {
	assertThrows(SQLRuntimeException.class, () -> db.procedure("{call UNEXISTINGPROCEDURE()}").call());
  }

  @Test
  public void testNoArgsProcedure() {
	assertEquals(10L, db.procedure("{call GETALLNAMES()}").execute(rs -> rs.getString("name")).peek(log::info).count());
  }

  @Test
  public void testProcedureGetResult() {
	assertEquals("name_1", db.procedure("{call GETNAMEBYID(?,?)}", P.in(1), P.out(JDBCType.VARCHAR)).call(cs -> cs.getString(2)).orElse(null));
  }

  @Test
  public void testProcedureGetResultNamed() {
	assertEquals("name_1", db.procedure("{call GETNAMEBYID(:in,:out)}", P.in("in", 1), P.out(JDBCType.VARCHAR, "out")).call(cs -> cs.getString(2)).orElse(null));
  }

  @Test
  public void testExceptionHandler() throws Throwable {
	assertThrows(Exception.class, () -> db.update("UPDATE TEST SET ID=? WHERE ID=?", 111, 1).poolable(true).timeout(0).execute());
  }

  @Test
  public void testPrimitives() {
	assertEquals(2, db.select("SELECT COUNT(*) FROM TEST WHERE id IN (:id)", entry("id", new long[]{1, 2})).single(rs -> rs.getInt(1)).orElse(-1).intValue());
	assertEquals(2, db.select("SELECT COUNT(*) FROM TEST WHERE id IN (:id)", entry("id", new int[]{1, 2})).single(rs -> rs.getInt(1)).orElse(-1).intValue());
	assertEquals(2, db.select("SELECT COUNT(*) FROM TEST WHERE id IN (:id)", entry("id", new byte[]{1, 2})).single(rs -> rs.getInt(1)).orElse(-1).intValue());
	assertEquals(2, db.select("SELECT COUNT(*) FROM TEST WHERE id IN (:id)", entry("id", new short[]{1, 2})).single(rs -> rs.getInt(1)).orElse(-1).intValue());
  }

  @Test
  public void testInvalidSelect() {
	assertThrows(IllegalArgumentException.class, () -> db.select("SELECT COUNT(*) FROM test WHERE id=:id", 1).single(rs -> rs.getInt(1)));
  }

  @Test
  public void testScript() {
	log.info(db.script(
			"CREATE TABLE TEST2(id int PRIMARY KEY generated always as IDENTITY, name VARCHAR(255) NOT NULL);" +
					"ALTER TABLE TEST2 ADD COLUMN surname VARCHAR(255);" +
					"INSERT INTO TEST2(name, surname) VALUES ('test1', 'test2');" +
					"DROP TABLE TEST2;" +
					"{call GETALLNAMES()};" +
					"call ECHO(:id);" +
					"SELECT * FROM TEST WHERE id=:id;" +
					"INSERT INTO TEST(name) VALUES(:value)",
			entry("id", 1),
			entry("value", "scripted_name")
	).print(log::info).verbose().timeout(1, TimeUnit.MINUTES).skipErrors(false).skipWarnings(false).execute());
	assertEquals("scripted_name", db.select("SELECT name FROM TEST WHERE name='scripted_name'").single(rs -> rs.getString(1)).orElse(""));
  }

  @Test
  public void testScriptWithNamedParameters() {
	log.info(db.script(
			"CREATE TABLE TEST2(id int PRIMARY KEY generated always as IDENTITY, name VARCHAR(255) NOT NULL);" +
					"ALTER TABLE TEST2 ADD COLUMN surname VARCHAR(255);" +
					"INSERT INTO TEST2(name, surname) VALUES (:name, :surname);" +
					"DROP TABLE TEST2;" +
					"{call GETALLNAMES()};",
			entry("name", "Name"),
			entry("surname", "SurName")
	).print(log::info).verbose().timeout(1, TimeUnit.MINUTES).execute());
  }

  @Test
  public void testEliminateComments() throws Exception {
	TryFunction<String, String, Exception> readFile = file -> {
	  try (BufferedReader r = new BufferedReader(
			  new InputStreamReader(requireNonNull(currentThread().getContextClassLoader().getResourceAsStream(file)))
	  )) {
		return r.lines().collect(joining("\r\n"));
	  }
	};
	String testCase1_in = "SELECT TO_CHAR(RTRIM(XMLAGG(XMLELEMENT(e, TO_CLOB('') || TO_CHAR(id) || ', ')).EXTRACT('*/text()').getClobVal(), ', ')) FROM DUAL";
	String testCase2_in = "SELECT TO_CHAR(RTRIM(XMLAGG(XMLELEMENT(e, TO_CLOB('') || TO_CHAR(id) || ', ')).EXTRACT('*/text()').getClobVal(), ', ')) AS \"/**/\" FROM DUAL";
	String testCase3_in = "SELECT TO_CHAR(RTRIM(XMLAGG(/*XMLELEMENT*/(e, TO_CLOB('') || TO_CHAR(id) || ', ')).EXTRACT('*/text()').getClobVal(), ', ')) AS \"/* whatever-label */\" FROM DUAL";
	String testCase3_out = "SELECT TO_CHAR(RTRIM(XMLAGG( (e, TO_CLOB('') || TO_CHAR(id) || ', ')).EXTRACT('*/text()').getClobVal(), ', ')) AS \"/* whatever-label */\" FROM DUAL";
	String testCase4_in = "SELECT TO_CHAR(RTRIM(XMLAGG(XMLELEMENT(e, TO_CLOB('') || TO_CHAR(id) || ', ')).EXTRACT('*/text()').getClobVal(), ', ')) AS \"/*--*/\" FROM DUAL";
	String testCase5_in = "-- \"/*\r\n--*//SELECT TO_CHAR(RTRIM(XMLAGG(XMLELEMENT(e, TO_CLOB('') || TO_CHAR(id) || ', ')).EXTRACT('*/text()').getClobVal(), ', ')) AS \"/*--*/\" FROM DUAL";
	String testCase6_in = "SELECT TO_CHAR(RTRIM(XMLAGG(XMLELEMENT(e, TO_CLOB('') || --TO_CHAR(id) || ', ')).EXTRACT('--/text()').getClobVal(), ', ')) FROM DUAL";
	String testCase6_out = "SELECT TO_CHAR(RTRIM(XMLAGG(XMLELEMENT(e, TO_CLOB('') ||";
	String testCase7_in = "**/";
	assertEquals(testCase1_in, wipeComments(testCase1_in));
	assertEquals(testCase2_in, wipeComments(testCase2_in));
	assertEquals(testCase3_out, wipeComments(testCase3_in));
	assertEquals(testCase4_in, wipeComments(testCase4_in));
	assertEquals("", wipeComments(testCase5_in));
	assertEquals(testCase6_out, wipeComments(testCase6_in));
	assertThrows(SQLRuntimeException.class, () -> wipeComments(testCase7_in));
	assertEquals(readFile.apply("script_out.sql"), wipeComments(readFile.apply("script_in.sql")));
  }

  @Test
  public void testInsertNull() {
	assertThrows(SQLRuntimeException.class, () -> db.update("INSERT INTO TEST(name) VALUES(:name)", entry("name", null)).execute());
  }

  @Test
  public void testToString() {
	DB db = DB.create(() -> conn);
	db.select("SELECT * FROM TEST WHERE name IN (:names)", entry("names", new Integer[]{1, 2}))
			.print(s -> assertEquals("SELECT * FROM TEST WHERE name IN (1,2)", s))
			.execute().count();
	db.update("UPDATE TEST SET NAME=:name WHERE NAME=:new_name", entry("name", "new_name_2"), entry("new_name", "name_2"))
			.print(s -> assertEquals("UPDATE TEST SET NAME='new_name_2' WHERE NAME='name_2'", s))
			.execute();
	db.update("INSERT INTO TEST(name) VALUES(?)", new Object[][]{{"name1"}, {"name2"}})
			.print(s -> assertEquals("INSERT INTO TEST(name) VALUES('name1');INSERT INTO TEST(name) VALUES('name2')", s))
			.execute();
	db.update("INSERT INTO TEST(name) VALUES(?)", "New_Name")
			.print(s -> assertEquals("INSERT INTO TEST(name) VALUES('New_Name')", s))
			.execute();
	db.procedure("{call CREATETESTROW2(?)}", "new_name")
			.print(s -> assertEquals("{call CREATETESTROW2(IN:=new_name(JAVA_OBJECT))}", s))
			.execute().count();
	db.script("SELECT * FROM TEST WHERE name=:name", entry("name", "name_2"))
			.print(s -> assertEquals("SELECT * FROM TEST WHERE name='name_2'", s))
			.execute();
  }

  @Test
  public void testUpdateWithGeneratedKeys() {
	Long id = db.update("INSERT INTO test(name) VALUES(?)", "name")
			.print(log::info)
			.execute(rs -> rs.getLong(1))
			.stream()
			.max(Comparator.comparing(i -> i))
			.flatMap(gId -> db.select("SELECT * FROM test WHERE id=?", gId).print(log::info).single(rs -> rs.getLong(1)))
			.orElse(-1L);
	assertEquals(11L, id.longValue());
	id = db.update("INSERT INTO test(name) VALUES(?)", "name")
			.print(log::info)
			.execute(rs -> rs.getLong(1), 1)
			.stream()
			.max(Comparator.comparing(i -> i))
			.flatMap(gId -> db.select("SELECT * FROM test WHERE id=?", gId).print(log::info).single(rs -> rs.getLong(1)))
			.orElse(-1L);
	assertEquals(12L, id.longValue());
	id = db.update("INSERT INTO test(name) VALUES(?)", "name")
			.print(log::info)
			.execute(rs -> rs.getLong(1), "ID")
			.stream()
			.max(Comparator.comparing(i -> i))
			.flatMap(gId -> db.select("SELECT * FROM test WHERE id=?", gId).print(log::info).single(rs -> rs.getLong(1)))
			.orElse(-1L);
	assertEquals(13L, id.longValue());
  }

  @Test
  public void testTransactions() {
	Long result = db.transaction().isolation(Transaction.Isolation.SERIALIZABLE)
			.execute(session ->
					session.update("INSERT INTO test(name) VALUES(?)", new Object[][]{{"name1"}, {"name2"}, {"name3"}})
							.batch(2)
							.skipWarnings(false)
							.timeout(1, TimeUnit.MINUTES)
							.print(log::info)
							.execute(rs -> rs.getLong(1))
							.stream()
							.peek(id -> session.procedure("call ECHO(?)", id).call())
							.max(Comparator.comparing(i -> i))
							.flatMap(gId -> session.select("SELECT * FROM test WHERE id=?", gId).print(log::info).single(rs -> rs.getLong(1)))
							.orElse(-1L)
			);
	log.info(db.select("SELECT * FROM test WHERE id=?", result).print(log::info).single());
	assertEquals(Long.valueOf(13L), result);
  }

  @Test
  public void testTransactionException() {
	DB db1 = DB.create(ds::getConnection);
	Long countBefore = db1.select("SELECT COUNT(*) FROM TEST").single(rs -> rs.getLong(1)).orElse(null);
	try {
	  db1.transaction().isolation(Transaction.Isolation.SERIALIZABLE).execute(session -> {
		session.update("INSERT INTO test(name) VALUES(?)", "name").execute();
		Long countAfter = session.select("SELECT COUNT(*) FROM TEST").single(rs -> rs.getLong(1)).orElse(null);
		assertEquals(countBefore + 1, (long) countAfter);
		throw new SQLException("Rollback!");
	  });
	} catch (Throwable t) {
	  t.printStackTrace();
	} finally {
	  Long countAfter = db1.select("SELECT COUNT(*) FROM TEST").single(rs -> rs.getLong(1)).orElse(null);
	  assertEquals(countBefore, countAfter);
	}
  }

  @Test
  public void testDeadlocksSingleConnectionSupplier() throws Exception {
	Connection conn = ds.getConnection();
	DB db1 = DB.builder().build(() -> conn);
	assertNotEquals(db1, db);
	Assertions.assertThrows(
			AssertionFailedError.class,
			() -> Assertions.assertTimeoutPreemptively(
					Duration.ofSeconds(5),
					() -> db1.transaction().run(session -> db1.transaction().run(session1 -> Thread.sleep(1000))),
					"execution timed out after 5000 ms"
			)
	);
	db1.close();
  }

  @Test
  public void testDeadlocksMultiConnectionSupplierMaxConnections1() throws Exception {
	DB db1 = DB.builder().withMaxConnections(1).build(ds::getConnection);
	assertNotEquals(db1, db);
	Assertions.assertThrows(
			AssertionFailedError.class,
			() -> Assertions.assertTimeoutPreemptively(
					Duration.ofSeconds(5),
					() -> db1.transaction().run(session -> db1.transaction().run(session1 -> {})),
					"execution timed out after 5000 ms"
			)
	);
	db1.close();
  }

  @Test
  public void testMaxConnectionsDriverManagerConnectionProvider() throws Exception {
	DB db1 = DB.builder()
			.withMaxConnections(3)
			.build(() -> DriverManager.getConnection("jdbc:derby:memory:test_dm;create=true"));
	db1.transaction().run(s1 -> db1.transaction().run(s2 -> db1.transaction().run(s3 -> {})));
	Assertions.assertThrows(
			AssertionFailedError.class,
			() -> Assertions.assertTimeoutPreemptively(
					Duration.ofSeconds(5),
					() -> db1.transaction().run(s1 -> db1.transaction().run(s2 -> db1.transaction().run(s3 -> db1.transaction().run(s4 -> {})))),
					"execution timed out after 5000 ms"
			)
	);
	db1.close();
  }

  @Test
  public void testNoNewConnectionSupplierWithTransaction() throws Exception {
	Connection conn = ds.getConnection();
	PrimitiveIterator.OfInt seq = Utils.newIntSequence();
	DB db1 = DB.builder().withTransactionIdProvider(() -> "" + seq.nextInt()).build(() -> conn);
	assertNotEquals(db1, db);
	ExecutorService executorService = Executors.newFixedThreadPool(2);
	CountDownLatch latch = new CountDownLatch(4);
	executorService.execute(() -> db1.transaction().execute(session -> {
	  Thread.sleep(2000);
	  log.info("T_1");
	  latch.countDown();
	  return null;
	}));
	executorService.execute(() -> db1.transaction().execute(session -> {
	  Thread.sleep(3000);
	  log.info("T_2");
	  latch.countDown();
	  return null;
	}));
	db1.transaction().run(session -> {
	  log.info("T_3");
	  latch.countDown();
	});
	db1.transaction().run(session -> {
	  Thread.sleep(2000);
	  log.info("T_4");
	  latch.countDown();
	});
	latch.await();
	executorService.shutdownNow();
	db1.close();
  }

  @Test
  public void testNestedTransactions() {
	List<String> list = db.transaction().execute((session1, ctx1) -> {
	  List<String> list1 = db.transaction().execute((session2, ctx2) -> session2.select("SELECT name FROM TEST1 WHERE id IN (:ids)",
			  entry("ids", session2.update("INSERT INTO TEST(name) VALUES(?)", "new_name").print(log::info).execute(rs -> rs.getLong(1)))
	  ).print(sql -> log.info("SQL: {}", sql)).execute(rs -> rs.getString(1)).collect(toList()));
	  assertNotNull(list1);
	  assertEquals(0L, list1.size());
	  return db.transaction()
			  .execute((session3, ctx) -> db.transaction()
					  .execute((session4, ctx4) -> session4.select("SELECT * FROM TEST").print(log::info)
							  .execute(rs -> rs.getString("name")).collect(toList())
					  )
			  );
	});
	assertNotNull(list);
	assertEquals(11L, list.size());
  }

  @Test
  public void testMaxConnections() throws Exception {
	DB db1 = DB.builder().withMaxConnections(1).build(ds::getConnection);
	Select select1 = db1.select("SELECT * FROM TEST");
	Select select2 = db1.select("SELECT COUNT(*) FROM TEST");
	Select select3 = db1.select("SELECT 1 FROM TEST");
	ExecutorService executorService = Executors.newFixedThreadPool(2);
	executorService.execute(() -> select1.execute().peek(log::info).collect(toList()));
	executorService.execute(() -> select2.execute().peek(log::info).count());
	executorService.execute(() -> select3.execute().peek(log::info).findFirst());
	Thread.sleep(5000);
	db1.close();
  }

  @Test
  public void testStoredProcedureRegexp() {
	Stream.of(
			entry("{call myProc()}", true),
			entry("{CALL MYPROC()}", true),
			entry("call myProc()", true),
			entry("{call myProc}", true),
			entry("call myProc", true),
			entry("{?=call MyProc()}", true),
			entry("?=call myProc()", true),
			entry("{?=call MyProc}", true),
			entry("?=call myProc", true),
			entry("{call myProc(?)}", true),
			entry("call myProc(?)", true),
			entry("{?=call myProc(?)}", true),
			entry("?=call myProc(?)", true),
			entry("{call myProc(?,?)}", true),
			entry("call myProc(?,?)", true),
			entry("{?=call myProc(?,?)}", true),
			entry("?=call myProc(?,?)", true),
			entry("{call myProc(?,?,?)}", true),
			entry("call myProc(?,?,?)", true),
			entry("{?=call myProc(?,?,?)}", true),
			entry("?=call myProc(?,?,?)", true),
			entry("{}", false),
			entry("call ", false),
			entry("{call}", false),
			entry("call myProc(?,?,?,?,?)", true),
			entry("call mySchema.myPackage.myProc()", true),
			entry("CALL MYSCHEMA.MYPACKAGE.MYPROC()", true),
			entry("call mySchema.myPackage.myProc(?)", true),
			entry("call mySchema.myPackage.myProc(?, ?)", true),
			entry("? = call mySchema.myPackage.myProc()", true),
			entry("? = call mySchema.myPackage.myProc(?)", true),
			entry("? = call mySchema.myPackage.myProc(?, ?)", true),
			entry("{call mySchema.myPackage.myProc()}", true),
			entry("{call mySchema.myPackage.myProc(?)}", true),
			entry("{call mySchema.myPackage.myProc(?, ?)}", true),
			entry("{? = call mySchema.myPackage.myProc()}", true),
			entry("{? = call mySchema.myPackage.myProc(?)}", true),
			entry("{? = call mySchema.myPackage.myProc(?, ?)}", true),
			entry("call mySchema.myPackage.myProc", true),
			entry("call mySchema....myPackage.myProc", false),
			entry("? = call mySchema.myPackage.myProc", true),
			entry("? = call mySchema.mySchema.myPackage.myProc", false)
			// TODO more cases here
	).forEach(testCase -> assertEquals(testCase.getValue(), Utils.STORED_PROCEDURE.matcher(testCase.getKey()).matches(), format("Test case '%s' failed", testCase.getKey())));
  }

  @Test
  public void testNamedParametersInStrings() {
	Map.Entry<String, Object[]> entry = prepareQuery("SELECT id AS \":ids :idss\" FROM TEST WHERE id IN(:ids)", singletonList(entry("ids", new int[]{1, 2, 3})));
	assertEquals("SELECT id AS \":ids :idss\" FROM TEST WHERE id IN(?,?,?)", entry.getKey());
	entry = prepareQuery("SELECT id AS \":ids :idss\" FROM TEST WHERE id IN(:ids1)", singletonList(entry("ids1", new int[]{1, 2, 3})));
	assertEquals("SELECT id AS \":ids :idss\" FROM TEST WHERE id IN(?,?,?)", entry.getKey());
	entry = prepareQuery(
			"SELECT id AS \":ids :idss\" FROM TEST WHERE id IN(:ids1)",
			Arrays.asList(entry("ids1", new int[]{1, 2, 3}), entry(":idss", new int[]{1, 2, 3}))
	);
	assertEquals("SELECT id AS \":ids :idss\" FROM TEST WHERE id IN(?,?,?)", entry.getKey());
	assertThrows(IllegalArgumentException.class, () -> prepareQuery("SELECT ':ids' FROM TEST WHERE id IN (:ids)", singletonList(entry("ids1", new int[]{1, 2, 3}))));
//	assertEquals("SELECT ':ids' FROM TEST WHERE id IN (?,?,?)", entry.getKey());
	assertTrue(isAnonymous("SELECT 1 AS \":one\""));
	assertThrows(IllegalArgumentException.class, () -> prepareQuery("SELECT id AS \":ids :idss\" FROM TEST WHERE id IN(:ids1)", singletonList(entry("ids2", new int[]{1, 2, 3}))));
  }

  @Test
  public void testNoReopening() {
	DB db1 = DB.create(ds::getConnection);
	assertEquals(10, db1.select("SELECT * FROM TEST").execute().count());
	db1.close();
	Throwable t = assertThrows(SQLRuntimeException.class, () -> db1.select("SELECT * FROM TEST").execute().count());
	assertEquals(t.getMessage(), "Connection pool is shutting down");
  }

  @Test
  public void testSingleQuery() {
	String query = "SELECT * FROM TEST';' SELECT * FROM TEST";
	checkSingle(query);
  }


  @Test
  public void testRawIteratorDenied() {
	assertThrows(UnsupportedOperationException.class, () -> db.select("SELECT * FROM TEST").forBatch(rs -> rs.getObject(1)).execute(TryConsumer.NOOP()).iterator());
  }

  @Test
  public void testRawSpliteratorDenied() {
	assertThrows(UnsupportedOperationException.class, () -> db.select("SELECT * FROM TEST").forBatch(rs -> rs.getObject(1)).execute(TryConsumer.NOOP()).spliterator());
  }

  @Test
  public void testStreamProxying() {
	Stream<Map<String, Object>> stream = db.select("SELECT * FROM TEST").execute().parallel().onClose(() -> log.info("stream close called"));
	log.info(stream.isParallel());
	assertThrows(UnsupportedOperationException.class, () -> stream.distinct().spliterator());
	Stream<Map<String, Object>> stream1 = db.select("SELECT * FROM TEST").execute().parallel().onClose(() -> log.info("stream1 close called"));
	assertThrows(UnsupportedOperationException.class, () -> stream1.distinct().iterator());
	Stream<Map<String, Object>> stream2 = db.select("SELECT * FROM TEST").execute().parallel().onClose(() -> log.info("stream2 close called"));
	log.info(stream2.collect(toList()));
  }

  @Test
  public void testNamedParams() {
	db.select("SELECT * FROM TEST WHERE id=:id AND id=:id", entry("id", 7)).print(log::info);
  }

  @Test
  public void testSelectReuseable() {
	Select select = db.select("SELECT * FROM TEST");
	assertEquals(10, select.execute().count());
	assertEquals(10, select.execute().count());
  }

  @Test
  public void testSelectNotThreadSafe() throws Exception {
	assertThrows(
			Exception.class,
			() -> {
			  int count = 10;
			  ExecutorService service = Executors.newFixedThreadPool(count);
			  CountDownLatch latch = new CountDownLatch(count);
			  Select shared = db.select("SELECT * FROM TEST");
			  AtomicReference<Throwable> exception = new AtomicReference<>();
			  Supplier<List<?>> list = () -> {
				try {
				  return shared.execute().collect(toList());
				} catch (Throwable t) {
				  exception.compareAndSet(null, t);
				  throw t;
				} finally {
				  latch.countDown();
				}
			  };
			  for (int i = 0; i < count; i++) {
				service.execute(list::get);
			  }
			  latch.await();
			  service.shutdownNow();
			  if (exception.get() != null) {
				throw exception.get();
			  }
			}
	);

  }

  @Test
  public void testSelectWithActions() {
	AtomicInteger i = new AtomicInteger();
	db.select("SELECT * FROM TEST").execute(rs -> {
	  List<Long> longs = db.update("INSERT INTO TEST(name) VALUES(?)", rs.getString("name") + i.incrementAndGet()).execute(r -> r.getLong(1));
	  return rs.getString("name") + longs;
	}).forEach(log::info);
	log.info(db.select("SELECT * FROM TEST").execute().collect(toList()));
  }

  @Test
  public void testBatchAndSequentialSelectionAreTheSame() throws Exception {
	List<?> sequential1 = db.select("SELECT * FROM TEST").execute().collect(toList());
	List<?> batch1 = db.select("SELECT * FROM TEST").forBatch().execute(TryConsumer.NOOP()).collect(toList());
	assertEquals(sequential1.size(), batch1.size());
	List<?> sequential2 = db.select("SELECT * FROM TEST WHERE ID = 20").execute().collect(toList());
	List<?> batch2 = db.select("SELECT * FROM TEST WHERE ID = 20").forBatch().execute(TryConsumer.NOOP()).collect(toList());
	assertEquals(sequential2.size(), batch2.size());
  }

  @Test
  public void testNewTransactions() {
	List<Map<String, Object>> list = db.transaction()
			.isolation(Transaction.Isolation.SERIALIZABLE)
			.onCommit(ctx -> log.info("Transaction {} committed successfully", ctx.transactionId()))
			.onRollback((e, ctx) -> log.info("Transaction {} rolled back due to {}", ctx.transactionId(), e))
			.execute(t -> t.select("SELECT * FROM TEST").execute().collect(toList()));
	log.info(list);
	db.transaction()
			.isolation(Transaction.Isolation.REPEATABLE_READ)
			.onCommit(ctx -> log.info("Transaction {} committed successfully", ctx.transactionId()))
			.onRollback((e, ctx) -> log.info("Transaction {} rolled back due to {}", ctx.transactionId(), e))
			.run(t -> {
			  t.update("INSERT INTO TEST(name) VALUES(?)", "new_name_2").execute();
			  throw new Exception("Rollback!");
			});
	assertEquals(10L, db.select("SELECT COUNT(*) FROM TEST").single(rs -> rs.getLong(1)).orElse(-1L).longValue());
  }

  @Test
  public void testSelectForBatch() throws Exception {
	db.select("SELECT * FROM TEST").forBatch().size(6).execute((batch, session, index) -> {
	  log.info("{} batch processing called!", index);
	  batch.forEach(map -> map.put("someKey" + index, "someValue" + index));
	  batch.add(new HashMap<String, Object>() {{
		put("ANY_KEY", "ANY_VALUE");
	  }});
	  session.select(
			  "SELECT * FROM TEST WHERE id IN (:ids)",
			  entry("ids", batch.stream().map(map -> (Integer) map.getOrDefault("ID", -1)).filter(i -> i > 0).collect(toList()))
	  ).execute().forEach(batch::add);
	}).forEach(log::info);
  }

  @Test
  public void testSelectForMeta() {
	assertEquals(2, db.select("SELECT * FROM TEST").forMeta(Metadata::getColumnFullNames).size());
  }

}
