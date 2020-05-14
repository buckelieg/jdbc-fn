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
  <version>0.1</version>
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
Collection<T> results = db.select("SELECT * FROM TEST WHERE ID IN (?, ?)", 1, 2).execute(rs ->{/*map ResultSet here*/}).collect(Collectors.toList());
// an alias for execute method is stream - for better readability
Collection<T> results = db.select("SELECT * FROM TEST WHERE ID IN (?, ?)", 1, 2).stream(rs ->{/*map ResultSet here*/}).collect(Collectors.toList());
// or use shorthands for stream reduction
Collection<T> results = db.select("SELECT * FROM TEST WHERE ID IN (?, ?)", 1, 2).list(rs ->{/*map ResultSet here*/});
```
or use named parameters:
```java
Collection<T> results = db.select("SELECT * FROM TEST WHERE 1=1 AND ID IN (:ID) OR NAME=:name", new HashMap<String, Object> {
          {
            put("ID", new Object[]{1, 2});
            put("name", "name_5"); // for example only. Do not use this IRL.
          }
}).execute(rs -> rs).reduce(
                new LinkedList<T>(),
                (list, rs) -> {
                    try {
                        list.add(...);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    return list;
                },
                (l1, l2) -> {
                  l1.addAll(l2);
                  return l1;
                }
        );
```
Parameter names are CASE SENSITIVE! 'Name' and 'name' are considered different parameter names.

### Insert 
with question marks:
```java
long res = db.update("INSERT INTO TEST(name) VALUES(?)", "New_Name").execute();
```
Or with named parameters:
```java
long res = db.update("INSERT INTO TEST(name) VALUES(:name)", new SimpleImmutableEntry<>("name","New_Name")).execute();
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
```
###### Batch mode
For batch operation use:
```java
long res = db.update("INSERT INTO TEST(name) VALUES(?)", new Object[][]{ {"name1"}, {"name2"} }).execute();
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
Script can contain single- and multiline comments. 
<br/>Each statement must be separated by a semicolon (";").
<br/>Script execution results ignored and not handled after all.

Note that scripts support named parameters and JDBC-like procedure call statements.

### Transactions
There are a couple of methods provide transaction support.
<br/>Tell whether to create new transaction or not, provide isolation level and transaction logic function.

```java
// suppose we have to insert a bunch of new users by name and get the latest one filled with its attributes....
User latestUser = db.transaction(false, TransactionIsolation.SERIALIZABLE, () ->
  db.update("INSERT INTO users(name) VALUES(?)", new Object[][]{{"name1"}, {"name2"}, {"name3"}})
    .skipWarnings(false)
    .timeout(1, TimeUnit.MINUTES)
    .print()
    .execute(
        rs -> rs.getLong(1),
        ids -> db.select("SELECT * FROM users WHERE id=?", ids.peek(id -> db.procedure("{call PROCESS_USER_CREATED_EVENT(?)}", id).call()).max(Comparator.comparing(i -> i)).orElse(-1L))
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
When calling <code>transaction()</code> method <code>createNew</code> flag (if set to <code>true</code>) implies obtaining new connection via <code>DataSource</code> or connection supplier function provided at the <code>DB</code> class [initialization](#setup-database) stage.
### Logging & Debugging
Convenient logging methods provided.
```java
Logger LOG = ... // configure logger
db.select("SELECT * FROM TEST WHERE id=?", 7).print(LOG::debug).list(rs -> {/*map rs here*/});
```
The above will print a current query to provided logger with debug method.
<br/>Note that all provided parameters will be substituted with corresponding values so this case will output:
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

### Going simple...
For cases when it is all about query execution on existing connection with no tuning, logging and other stuff the <code>Queries</code> helper class can be used.
<br/>For example:
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
#### ...even simpler
There is an option to set up the connection with helper class to reduce a number of method arguments:
```java
Connection conn = ... // somewhere previously created connection
Queries.setConnection(conn);
// all subsequent calls will be done on connection set.
List<String> names = Queries.list(rs -> rs.getString("name"), "SELECT name FROM TEST WHERE id IN (:ids)", new SimpleImmutableEntry("ids", new long[]{1, 2, 3}));
List<String> names = Queries.callForList(rs -> rs.getString(1), "{call GETALLNAMES()}");
```
#### ...even simplest...
with built-in mapper functionality.
<br/>All <code>Select</code> query methods which takes a <code>mapper</code> function has a companion one without.
<br/> Calling that <code>mapper</code>-less methods will imply mapping a tuple as <code>String</code> alias to <code>Object</code> value.
<br/>For example:
```java
Connection conn = ... // somewhere previously created connection
Queries.setConnection(conn);
List<Map<String, Object>> names = Queries.list("SELECT name FROM TEST WHERE id IN (:ids)", new SimpleImmutableEntry("ids", new long[]{1, 2, 3}));
```
So that there are minimum efforts to obtain data from database.
<br/>Note that connection must be closed explicitly after using <code>Queries</code> helper.

### Prerequisites
Java8, Maven, Appropriate JDBC driver.

## License
This project licensed under Apache License, Version 2.0 - see the [LICENSE.md](LICENSE.md) file for details

