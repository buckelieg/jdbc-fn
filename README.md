[![build](https://github.com/buckelieg/jdbc-fn/actions/workflows/ci.yml/badge.svg)](https://github.com/buckelieg/jdbc-fn/actions/workflows/ci.yml)
[![license](https://img.shields.io/github/license/buckelieg/jdbc-fn.svg)](./LICENSE.md)
[![dist](https://img.shields.io/maven-central/v/com.github.buckelieg/jdbc-fn.svg)](http://mvnrepository.com/artifact/com.github.buckelieg/jdbc-fn)
[![javadoc](https://javadoc.io/badge2/com.github.buckelieg/jdbc-fn/javadoc.svg)](https://javadoc.io/doc/com.github.buckelieg/jdbc-fn)
[![codecov](https://codecov.io/github/buckelieg/jdbc-fn/graph/badge.svg?token=4uHlR7gi8v)](https://codecov.io/github/buckelieg/jdbc-fn)
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
  <version>1.0</version>
</dependency>
```

### Setup database
There are a couple of ways to set up the things:
```java
DataSource ds = ... // obtain ds (e.g. via JNDI or other way)
DB db = DB.create(ds::getConnection); // shortcut for DB.builder().build(ds::getConnection)
// or
DB db = DB.builder()
          .withMaxConnections(10) // defaults to Runtime.getRuntime().availableProcessors()
          .build(() -> DriverManager.getConnection("vendor-specific-string"));
// do things...
db.close(); // cleaning used resources: closes underlying connection pool, executor service (if configured to do so) etc...
```

### Select
Use question marks:
```java
Collection<String> names = db.select("SELECT name FROM TEST WHERE ID IN (?, ?)", 1, 2).execute(rs -> rs.getString("name")).collect(Collectors.toList());
```
or use named parameters:
```java
// in java9+
import static java.util.Map.of;
Collection<String> names = db.select(
		"SELECT name FROM TEST WHERE 1=1 AND ID IN (:ID) OR NAME=:name", 
                of(":ID", new Object[]{1, 2}, ":name", "name_5")
        ).execute(rs -> rs.getString("name")).reduce(
                new LinkedList<>(),
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

##### The N+1 problem resolution
For the cases when it is needed to process (say - enrich) each mapped row with an additional data the `Select.ForBatch` can be used
```java
Stream<Entity> entities = db.select("SELECT * FROM HUGE_TABLE")
        .forBatch(/* map resultSet here to needed type*/)
        .size(1000)
        .execute(batchOfObjects -> {
		  // list of mapped rows with size not more than 1000
		  batchOfObjects.forEach(obj -> obj.setSomethingElse());
        });
```
For cases where it is needed to issue any additional queries to database use:
```java
// suppose the USERS table contains thousands of records
Stream<User> users = db.select("SELECT * FROM USERS")
    .forBatch(rs -> new User(rs.getLong("id"), rs.getString("name")))
    .size(1000)
    .execute((batchOfUsers, session) -> {
	  Map<Long, UserAttr> attrs = session.select(
		"SELECT * FROM USER_ATTR WHERE id IN (:ids)",
                entry("ids", batchOfUsers.stream().map(User::getId).collect(Collectors.toList()))
          ).execute(rs -> {
			UserAttr attr = new UserAttr();
			attr.setId(rs.getLong("attr_id"));
			attr.setUserId(rs.getLong("user_id"));
			attr.setName(rs.getString("attr_name"));
			// etc...
			return attr;
		  })
          .groupingBy(UserAttr::userId, Function.identity());
	  batchOfUsers.forEach(user -> user.addAttrs(attrs.getOrDefault(user.getId(), Collections.emptyList())));
	});
// stream of users objects will consist of updated (enriched) objects
```
Using this to process batches you must keep some things in mind:
+ Executor service is used internally to power parallel processing</li>
+ All batches are processed regardless any possible short circuits</li>
+ <code>Select.fetchSize</code> and <code>Select.ForBatch.size</code> are not the same but connected</li>

##### Metadata processing
For the special cases when only a metadata of the query is needed `Select.forMeta` can be used:
```java
// suppose we want to collect information of which column of the provided query is a primary key
Map<String, Boolean> processedMeta = db.select("SELECT * FROM TEST").forMeta(metadata -> {
  Map<String, Boolean> map = new HashMap<>();
  metadata.forEachColumn(columnIndex -> map.put(metadata.getName(columnIndex), metadata.isPrimaryKey(columnIndex)));
  return map;
});
```

### Insert 
with question marks:
```java
// res is an affected rows count
long res = db.update("INSERT INTO TEST(name) VALUES(?)", "New_Name").execute();
```
Or with named parameters:
```java
long res = db.update("INSERT INTO TEST(name) VALUES(:name)", new SimpleImmutableEntry<>("name","New_Name")).execute();
// in java9+
long res = db.update("INSERT INTO TEST(name) VALUES(:name)", Map.entry("name","New_Name")).execute();
```
##### Getting generated keys
To retrieve possible generated keys provide a mapping function to `execute` method:
```java
Collection<Long> generatedIds = db.update("INSERT INTO TEST(name) VALUES(?)", "New_Name").execute(rs -> rs.getLong(1));
```
See docs for more options.
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
long res = db.update("UPDATE TEST SET NAME=:name WHERE NAME=:new_name", Map.entry(":name", "new_name_2"), Map.entry(":new_name", "name_2")).execute();
```
##### Batch mode
For batch operation use:
```java
long res = db.update("INSERT INTO TEST(name) VALUES(?)", new Object[][]{ {"name1"}, {"name2"} }).batch(2).execute();
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

+ Provide a script itself
```java
db.script("CREATE TABLE TEST (id INTEGER NOT NULL, name VARCHAR(255));INSERT INTO TEST(id, name) VALUES(1, 'whatever');UPDATE TEST SET name = 'whatever_new' WHERE name = 'whatever';DROP TABLE TEST;").execute();
```
+ Provide a file with an SQL script
```java
db.script(new File("path/to/script.sql")).timeout(60).execute();
```
Script:
+ Can contain single- and multiline comments. 
+ Each statement must be separated by a semicolon (";").
+ Execution results ignored and not handled after all.
+ Support named parameters
+ Support escaped syntax, so it is possible to include JDBC-like procedure call statements.

### Transactions
Long story short - an example:

```java
// suppose we have to insert a bunch of new users by name and get the latest one filled with its attributes....

Logger LOG = getLogger(); //... logger used in application 
User latestUser = db.transaction()
  .isolation(Transaction.Isolation.SERIALIZABLE)
  .execute(session ->
      session.update("INSERT INTO users(name) VALUES(?)", new Object[][]{ {"name1"}, {"name2"}, {"name3"} })
        .skipWarnings(false)
        .timeout(1, TimeUnit.MINUTES)
        .print(LOG::debug)
        .execute(rs -> rs.getLong(1))
        .stream()
        .peek(id -> session.procedure("{call PROCESS_USER_CREATED_EVENT(?)}", id).call())
        .max(Comparator.comparing(i -> i))
        .flatMap(id -> session.select("SELECT * FROM users WHERE id=?", id).print(LOG::debug).single(rs -> {
		  User u = new User();
		  u.setId(rs.getLong("id"));
		  u.setName(rs.getString("name"));
		  // ...fill other user's attributes...
		  return user;
        }))
        .orElse(null)
);
```
##### Nested transactions and deadlocks
Providing connection supplier function with plain connection
<br/>like this: ```DB db = DB.create(() -> connection));```
<br/>or this: &nbsp;&nbsp;```DB db = DB.builder().withMaxConnections(1).build(() -> DriverManager.getConnection("vendor-specific-string"));```
<br/> e.g - if supplier function always return the same connection
<br/>the concept of transactions will be partially broken.

The simplest case:
```java
DB db = DB.create(() -> connection); // or DB.builder().withMaxConnections(1).build(ds::getConnection)
db.transaction().run(session1 -> db.transaction().run(session2 -> {}))
// runs forever since each transaction tries to obtain new connection and the second one cannot be provided with new one
```

### Logging & Debugging
Convenient logging methods provided.
```java
Logger LOG = // ... 
db.select("SELECT * FROM TEST WHERE id=?", 7).print(LOG::debug).single(rs -> {/*map rs here*/});
```
The above will print a current query to provided logger with debug method.
<br/>All provided parameters will be substituted with corresponding values so this case will output:
<br/><code>SELECT * FROM TEST WHERE id=7</code>
<br/>Calling <code>print()</code> without arguments will do the same with standard output.

##### Scripts logging
For <code>Script</code> query <code>verbose()</code> method can be used to track current script step execution.
```java
db.script("SELECT * FROM TEST WHERE id=:id;DROP TABLE TEST", new SimpleImmutableEntry<>("id", 5)).verbose().execute();
```
This will print out to standard output two lines:
<br/>```SELECT * FROM TEST WHERE id=5```
<br/>```DROP TABLE TEST```
<br/>Each line will be appended to output at the moment of execution.
<br/>Calling ```print()``` on ```Script``` will print out the whole sql script with parameters substituted.
<br/>Custom logging handler may also be provided for both cases.

### Built-in mappers
All ```Select``` query methods which takes a ```mapper``` function has a companion one without.
<br/> Calling that ```mapper```-less methods will imply mapping to a tuple as ```String``` alias to ```Object``` value:
```java
List<Map<String, Object>> = db.select("SELECT name FROM TEST").execute().collect(Collectors.toList());
```

### Prerequisites
Java8, Maven, Appropriate JDBC driver.

## License
This project licensed under Apache License, Version 2.0 - see the [LICENSE.md](LICENSE.md) file for details

