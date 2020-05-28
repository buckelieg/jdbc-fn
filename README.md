[![build](https://github.com/buckelieg/jdbc-fn/workflows/build/badge.svg?branch=master)]()
[![license](https://img.shields.io/github/license/buckelieg/jdbc-fn.svg)](./LICENSE.md)
[![dist](https://img.shields.io/maven-central/v/com.github.buckelieg/jdbc-fn.svg)](http://mvnrepository.com/artifact/com.github.buckelieg/jdbc-fn)
[![javadoc](https://javadoc.io/badge2/com.github.buckelieg/jdbc-fn/javadoc.svg)](https://javadoc.io/doc/com.github.buckelieg/jdbc-fn)
# jdbc-fn
Functional style programming over plain JDBC.
+ Execute SQL [SELECT](#select) query and process results with Java Stream API.
+ Fire SQL [UPDATE(S)](#update) [batch(es)](#batch-mode) in a single line of code.
+ Invoke [stored procedures](#stored-procedures) and retrieve its results without dealing with SQLExceptions
+ Execute parameterized [scripts](#scripts).

And more...

## Getting Started
Add maven dependency:
```
<dependency>
  <groupId>com.github.buckelieg</groupId>
  <artifactId>jdbc-fn</artifactId>
  <version>0.3</version>
</dependency>
```

### Setup database
There are a couple of ways to set up the things:
```java
// 1. Provide DataSource
DataSource ds = // obtain ds (e.g. via JNDI or other way) 
DB db = new DB(ds);
// 2. Provide connection supplier
DB db = new DB(ds::getConnection);
// or
DB db = new DB(() -> {/*sophisticated connection supplier function*/});
// do things...
db.close();
// DB can be used in try-with-resources statements
try (DB db = new DB(/*init*/)) {
    ...
} finally {
    
}
```
Note that providing connection supplier function with plain connection 
<br/>like this: <code>DB db = new DB(() -> connection));</code> 
<br/>or this: &nbsp;&nbsp;<code>DB db = new DB(() -> DriverManager.getConnection("vendor-specific-string"));</code> 
<br/> e.g - if supplier function always return the same connection
<br/>the concept of transactions will be partially broken: see [Transactions](#transactions) section.

### Select
Use question marks:
```java
Collection<String> names = db.select("SELECT name FROM TEST WHERE ID IN (?, ?)", 1, 2).execute(rs -> rs.getString("name")).collect(Collectors.toList());
// an alias for execute method is stream - for better readability
Collection<String> names = db.select("SELECT name FROM TEST WHERE ID IN (?, ?)", 1, 2).stream(rs -> rs.getString("name")).collect(Collectors.toList());
// or use shorthands for stream reduction
Collection<String> names = db.select("SELECT name FROM TEST WHERE ID IN (?, ?)", 1, 2).list(rs -> rs.getString("name"));
```
or use named parameters:
```java
// in java9+
import static java.util.Map.of;
Collection<String> names = db.select("SELECT name FROM TEST WHERE 1=1 AND ID IN (:ID) OR NAME=:name", of(":ID", new Object[]{1, 2}, ":name", "name_5")).execute(rs -> rs.getString("name"))
        .reduce(
                new LinkedList<T>(),
                (list, name) -> {
                    list.add(name);
                    return list;
                },
                (l1, l2) -> {
                  l1.addAll(l2);
                  return l1;
                }
        );
```
Parameter names are CASE SENSITIVE! 'Name' and 'name' are considered different parameter names.
<br/> Parameters may be provided with or without leading colon.

### Insert 
with question marks:
```java
long res = db.update("INSERT INTO TEST(name) VALUES(?)", "New_Name").execute();
```
Or with named parameters:
```java
long res = db.update("INSERT INTO TEST(name) VALUES(:name)", new SimpleImmutableEntry<>("name","New_Name")).execute();
// in java9+
import static java.util.Map.entry;
long res = db.update("INSERT INTO TEST(name) VALUES(:name)", entry("name","New_Name")).execute();
```
### Update
```java
long res = db.update("UPDATE TEST SET NAME=? WHERE NAME=?", "new_name_2", "name_2").execute();
```
or
```java
long res = db.update("UPDATE TEST SET NAME=:name WHERE NAME=:new_name", 
  new SimpleImmutableEntry<>("name", "new_name_2"), 
  new SimpleImmutableEntry<>("new_name", "name_2")
).execute();
// in java9+
import static java.util.Map.entry;
long res = db.update("UPDATE TEST SET NAME=:name WHERE NAME=:new_name", entry(":name", "new_name_2"), entry(":new_name", "name_2")).execute();
```
###### Batch mode
For batch operation use:
```java
long res = db.update("INSERT INTO TEST(name) VALUES(?)", new Object[][]{ {"name1"}, {"name2"} }).batch(true).execute();
```  
### Delete
```java
long res = db.update("DELETE FROM TEST WHERE name=?", "name_2").execute();
```

### Stored Procedures
Invoking stored procedures is also quite simple:
```java
String name = db.procedure("{call GETNAMEBYID(?,?)}", P.in(12), P.out(JDBCType.VARCHAR)).call(cs -> cs.getString(2)).orElse("Unknown");
```
Note that in the latter case stored procedure must not return any result sets.
<br/>If stored procedure is considered to return result sets it is handled similar to regular selects (see above).

### Scripts
There are two options to run an arbitrary SQL scripts:

1) Provide a script itself
```java
db.script("CREATE TABLE TEST (id INTEGER NOT NULL, name VARCHAR(255));INSERT INTO TEST(id, name) VALUES(1, 'whatever');UPDATE TEST SET name = 'whatever_new' WHERE name = 'whatever';DROP TABLE TEST;").execute();
```
2) Provide a file with an SQL script
```java
  db.script(new File("path/to/script.sql")).timeout(60).execute();
```
Script:
<br/>Can contain single- and multiline comments. 
<br/>Each statement must be separated by a semicolon (";").
<br/>Execution results ignored and not handled after all.
<br/>Support named parameters
<br/>Support escaped syntax, so it is possible to include JDBC-like procedure call statements.

### Transactions
There are a couple of methods provides transaction support.
<br/>Tell whether to create new transaction or not, provide isolation level and transaction logic function.
```java
// suppose we have to insert a bunch of new users by name and get the latest one filled with its attributes....
User latestUser = db.transaction(TransactionIsolation.SERIALIZABLE, db1 ->
  // here db.equals(db1) will return true
  // but if we claim to create new transaction it will not, because a new connection is obtained and new DB instance is created
  // so everything inside a transaction (in this case) MUST be done through db1 reference since it will operate on newly created connection   
  db1.update("INSERT INTO users(name) VALUES(?)", new Object[][]{ {"name1"}, {"name2"}, {"name3"} })
    .skipWarnings(false)
    .timeout(1, TimeUnit.MINUTES)
    .print()
    .execute(
        rs -> rs.getLong(1),
        ids -> db1.select("SELECT * FROM users WHERE id=?", ids.peek(id -> db1.procedure("{call PROCESS_USER_CREATED_EVENT(?)}", id).call()).max(Comparator.comparing(i -> i)).orElse(-1L))
                 .print()
                 .single(rs -> {
                     User u = new User();
                     u.setId(rs.getLong("id"));
                     u.setName(rs.getString("name"));
                     //... fill other user's attributes...
                     return user;
                 })
    )
    .orElse(null)
);
```
As the rule of thumb: always use lambda parameter to do the things inside the transaction
###### Nested transactions
This must be used with care.
<br/>When calling <code>transaction()</code> method <code>createNew</code> flag (if set to <code>true</code>) implies obtaining new connection via <code>DataSource</code> or connection supplier function provided at the <code>DB</code> class [initialization](#setup-database) stage.
<br/>If provided connection supplier function will not return a new connection - then <code>UnsupportedOperationException</code> is thrown:
```java
DB db = new DB(() -> connection);
db.transaction(TransactionIsolation.SERIALIZABLE, db1 -> db1.transaction(true, db2 -> ...))
// throws UnsupportedOperationException
```
Using nested transactions with various isolation levels may result in deadlocks:
```java
DB db = new DB(datasourceInstance);
db.transaction(TransactionIsolation.READ_UNCOMMITED, db1 -> {
    // do inserts, updates etc...
    long someGeneratedId = ....
    return db1.transaction(true, TransactionIsolation.SERIALIZABLE, db2 -> db2.select("SELECT * FROM TEST WHERE id=?", someGeneratedId).list(rs -> rs.getString("name")));
});
// nested transaction will be done over newly obtained connection but will not able to complete or see the generated values before enclosing transaction is committed and will eventually fail
```
Whenever desired transaction isolation level is not supported by RDBMS the <code>IllegalArgumentException</code> is thrown.
### Logging & Debugging
Convenient logging methods provided.
```java
Logger LOG = ... // configure logger
db.select("SELECT * FROM TEST WHERE id=?", 7).print(LOG::debug).list(rs -> {/*map rs here*/});
```
The above will print a current query to provided logger with debug method.
<br/>All provided parameters will be substituted with corresponding values so this case will output:
<br/><code>SELECT * FROM TEST WHERE id=7</code>
<br/>Calling <code>print()</code> without arguments will do the same with standard output.

###### Scripts logging
For <code>Script</code> query <code>verbose()</code> method can be used to track current script query execution.
```java
db.script("SELECT * FROM TEST WHERE id=:id;DROP TABLE TEST", new SimpleImmutableEntry<>("id", 5)).verbose().execute();
```
This will print out to standard output two lines:
<br/><code>SELECT * FROM TEST WHERE id=5</code>
<br/><code>DROP TABLE TEST</code>
<br/>Each line will be appended to output at the moment of execution.
<br/>Calling <code>print()</code> on <code>Script</code> will print out the whole sql script with parameters substituted.
<br/>Custom logging handler may also be provided for both cases.

### Helper: Queries
For cases when it is all about query execution on existing connection with no tuning, logging and other stuff the <code>Queries</code> helper class can be used:
```java
Connection conn = ... // somewhere previously created connection
List<String> names = Queries.list(conn, rs -> rs.getString("name"), "SELECT name FROM TEST WHERE id IN (:ids)", new SimpleImmutableEntry("ids", new long[]{1, 2, 3}));
```
There are plenty of pre-defined cases implemented:
<br/><code>list</code> - for list selection 
<br/><code>single</code> - for single object selection, 
<br/><code>callForList</code> - calling <code>StoredProcedure</code> which returns a <code>ResultSet</code>,
<br/><code>call</code> - call a <code>StoredProcedure</code> either with results or without,
<br/><code>update</code> - to execute various updates,
<br/><code>execute</code> - to execute atomic queries and/or scripts
<br/>
<br/>There is an option to set up the connection with helper class to reduce a number of method arguments:
```java
Connection conn = ... // somewhere previously created connection
Queries.setConnection(conn);
// all subsequent calls will be done on connection set.
List<String> names = Queries.list(rs -> rs.getString("name"), "SELECT name FROM TEST WHERE id IN (:ids)", new SimpleImmutableEntry("ids", new long[]{1, 2, 3}));
List<String> names = Queries.callForList(rs -> rs.getString(1), "{call GETALLNAMES()}");
```
Note that connection must be closed explicitly after using <code>Queries</code> helper.
### Built-in mappers
All <code>Select</code> query methods which takes a <code>mapper</code> function has a companion one without.
<br/> Calling that <code>mapper</code>-less methods will imply mapping a tuple as <code>String</code> alias to <code>Object</code> value:
```java
// DB
DB db = new DB(datasourceInstance);
List<Map<String, Object>> = db.select("SELECT name FROM TEST").list();
// Queries
Connection conn = ... // somewhere previously created connection
Queries.setConnection(conn);
List<Map<String, Object>> names = Queries.list("SELECT name FROM TEST WHERE id IN (:ids)", new SimpleImmutableEntry("ids", new long[]{1, 2, 3}));
```

### Prerequisites
Java8, Maven, Appropriate JDBC driver.

## License
This project licensed under Apache License, Version 2.0 - see the [LICENSE.md](LICENSE.md) file for details

