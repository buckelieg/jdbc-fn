package buckelieg.jdbc;

import buckelieg.fn.TrySupplier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultConnectionManagerTests {

  @Test
  void reusesOnePhysicalConnection() throws Exception {
	AtomicInteger supplied = new AtomicInteger();
	DefaultConnectionManager manager = manager(() -> {
	  supplied.incrementAndGet();
	  return connection();
	}, 1);
	try {
	  for (int i = 0; i < 20; i++) {
		Connection connection = manager.getConnection();
		manager.close(connection);
	  }
	  assertEquals(1, supplied.get());
	} finally {
	  manager.close();
	}
  }

  @Test
  void neverExceedsConfiguredMaximum() throws Exception {
	AtomicInteger supplied = new AtomicInteger();
	DefaultConnectionManager manager = manager(() -> {
	  supplied.incrementAndGet();
	  return connection();
	}, 2);
	ExecutorService workers = Executors.newFixedThreadPool(12);
	CountDownLatch start = new CountDownLatch(1);
	List<Future<?>> tasks = new ArrayList<>();
	try {
	  for (int i = 0; i < 12; i++) {
		tasks.add(workers.submit(() -> {
		  start.await();
		  Connection connection = manager.getConnection();
		  try {
			Thread.sleep(10);
		  } finally {
			manager.close(connection);
		  }
		  return null;
		}));
	  }
	  start.countDown();
	  for (Future<?> task : tasks) task.get(5, TimeUnit.SECONDS);
	  assertTrue(supplied.get() <= 2, "Created connections: " + supplied.get());
	} finally {
	  workers.shutdownNow();
	  manager.close();
	}
  }

  @Test
  void releasesSlotAfterSupplierFailure() throws Exception {
	AtomicInteger attempts = new AtomicInteger();
	DefaultConnectionManager manager = manager(() -> {
	  if (attempts.getAndIncrement() == 0) throw new SQLException("expected");
	  return connection();
	}, 1);
	try {
	  assertThrows(SQLException.class, manager::getConnection);
	  Connection connection = manager.getConnection();
	  manager.close(connection);
	  assertEquals(2, attempts.get());
	} finally {
	  manager.close();
	}
  }

  @Test
  void releasesSlotAfterNullSupplierResult() throws Exception {
	AtomicInteger attempts = new AtomicInteger();
	DefaultConnectionManager manager = manager(
			() -> attempts.getAndIncrement() == 0 ? null : connection(),
			1
	);
	try {
	  assertThrows(SQLException.class, manager::getConnection);
	  Connection connection = manager.getConnection();
	  manager.close(connection);
	  assertEquals(2, attempts.get());
	} finally {
	  manager.close();
	}
  }

  @Test
  void releasesSlotAfterPooledConnectionValidationFailure() throws Exception {
	AtomicInteger supplied = new AtomicInteger();
	AtomicInteger closes = new AtomicInteger();
	Connection failing = connectionThatFailsThirdValidation(closes);
	DefaultConnectionManager manager = manager(
			() -> supplied.getAndIncrement() == 0 ? failing : connection(),
			1
	);
	try {
	  Connection connection = manager.getConnection();
	  manager.close(connection);

	  assertThrows(SQLException.class, manager::getConnection);

	  Connection replacement = manager.getConnection();
	  manager.close(replacement);
	  assertEquals(2, supplied.get());
	  assertEquals(1, closes.get());
	} finally {
	  manager.close();
	}
  }

  @Test
  void releasesSlotAfterMetadataCaptureFailure() throws Exception {
	AtomicInteger supplied = new AtomicInteger();
	AtomicInteger closes = new AtomicInteger();
	DefaultConnectionManager manager = manager(
			() -> supplied.getAndIncrement() == 0 ? connectionThatFailsMetadataCapture(closes) : connection(),
			1
	);
	try {
	  assertThrows(SQLException.class, manager::getConnection);

	  Connection replacement = manager.getConnection();
	  manager.close(replacement);
	  assertEquals(2, supplied.get());
	  assertEquals(1, closes.get());
	} finally {
	  manager.close();
	}
  }

  @Test
  void releasesSlotAfterConnectionInitializationFailure() throws Exception {
	AtomicInteger supplied = new AtomicInteger();
	AtomicInteger closes = new AtomicInteger();
	DefaultConnectionManager manager = manager(
			() -> supplied.getAndIncrement() == 0 ? connectionThatFailsInitialization(closes) : connection(),
			1
	);
	try {
	  assertThrows(SQLException.class, manager::getConnection);

	  Connection replacement = manager.getConnection();
	  manager.close(replacement);
	  assertEquals(2, supplied.get());
	  assertEquals(1, closes.get());
	} finally {
	  manager.close();
	}
  }

  @Test
  void successfulStandaloneUpdateCommitsBeforeReturningConnection() throws Exception {
	UpdateProbe probe = new UpdateProbe(false);
	DefaultConnectionManager manager = manager(probe::connection, 1);
	ExecutorService executor = Executors.newSingleThreadExecutor();
	try {
	  UpdateQuery update = new UpdateQuery(
			  manager::getConnection,
			  manager::close,
			  executor,
			  "UPDATE TEST SET VALUE=?",
			  new Object[][]{{1}, {2}}
	  );

	  assertEquals(2L, update.execute());
	  assertEquals(1, probe.commits.get());
	  assertEquals(0, probe.rollbacks.get());
	} finally {
	  executor.shutdownNow();
	  manager.close();
	}
  }

  @Test
  void failedStandaloneUpdateRollsBackPartialWork() throws Exception {
	UpdateProbe probe = new UpdateProbe(true);
	DefaultConnectionManager manager = manager(probe::connection, 1);
	ExecutorService executor = Executors.newSingleThreadExecutor();
	try {
	  UpdateQuery update = new UpdateQuery(
			  manager::getConnection,
			  manager::close,
			  executor,
			  "UPDATE TEST SET VALUE=?",
			  new Object[][]{{1}, {2}}
	  );

	  assertThrows(SQLRuntimeException.class, update::execute);
	  assertEquals(0, probe.commits.get());
	  assertEquals(1, probe.rollbacks.get());
	} finally {
	  executor.shutdownNow();
	  manager.close();
	}
  }

  @Test
  void returnWithoutOutcomeNeverCommits() throws Exception {
	Probe probe = new Probe(false, false);
	DefaultConnectionManager manager = manager(probe::connection, 1);
	try {
	  Connection connection = manager.getConnection();
	  manager.close(connection);

	  assertEquals(0, probe.commits.get());
	  assertEquals(1, probe.rollbacks.get());
	} finally {
	  manager.close();
	}
  }

  @Test
  @SuppressWarnings("unchecked")
  void streamTerminalOperationReportsItsOutcomeBeforeClosing() {
	AtomicBoolean succeeded = new AtomicBoolean();
	AtomicBoolean failed = new AtomicBoolean();
	Stream<Integer> successful = (Stream<Integer>) Utils.proxy(
			Stream.of(1),
			() -> succeeded.set(true),
			() -> failed.set(true)
	);
	assertEquals(1L, successful.count());
	assertTrue(succeeded.get());
	assertFalse(failed.get());

	succeeded.set(false);
	failed.set(false);
	Stream<Integer> broken = (Stream<Integer>) Utils.proxy(
			Stream.of(1).map(value -> {
			  throw new IllegalStateException("expected stream failure");
			}),
			() -> succeeded.set(true),
			() -> failed.set(true)
	);
	assertThrows(SQLRuntimeException.class, () -> broken.forEach(value -> {}));
	assertFalse(succeeded.get());
	assertTrue(failed.get());
  }

  @Test
  void waitingCheckoutStopsWhenManagerCloses() throws Exception {
	DefaultConnectionManager manager = manager(DefaultConnectionManagerTests::connection, 1);
	manager.getConnection();
	ExecutorService worker = Executors.newSingleThreadExecutor();
	CountDownLatch started = new CountDownLatch(1);
	Future<Throwable> waitingCheckout = worker.submit(() -> {
	  started.countDown();
	  try {
		manager.getConnection();
		return new AssertionError("Checkout unexpectedly succeeded after shutdown");
	  } catch (Throwable expected) {
		return expected;
	  }
	});
	try {
	  assertTrue(started.await(1, TimeUnit.SECONDS));
	  Thread.sleep(200);
	  assertThrows(SQLException.class, manager::close);
	  assertInstanceOf(SQLException.class, waitingCheckout.get(2, TimeUnit.SECONDS));
	} finally {
	  worker.shutdownNow();
	}
  }

  @Test
  void keepAliveDoesNotChangeTransactionState() throws Exception {
	Probe probe = new Probe(false, false);
	DefaultConnectionManager manager = manager(probe::connection, 1);
	try {
	  Connection connection = manager.getConnection();
	  manager.close(connection);
	  makeIdle(manager, connection);
	  int autoCommitChangesBefore = probe.autoCommitChanges.get();
	  int rollbacksBefore = probe.rollbacks.get();

	  invokeKeepAlive(manager);

	  assertEquals(1, probe.executions.get());
	  assertEquals(autoCommitChangesBefore, probe.autoCommitChanges.get());
	  assertEquals(rollbacksBefore, probe.rollbacks.get());
	} finally {
	  manager.close();
	}
  }

  @Test
  void keepAliveLeasesConnectionExclusively() throws Exception {
	Probe probe = new Probe(true, false);
	DefaultConnectionManager manager = manager(probe::connection, 1);
	Connection connection = manager.getConnection();
	manager.close(connection);
	makeIdle(manager, connection);
	ExecutorService workers = Executors.newFixedThreadPool(2);
	Future<?> keepAlive = workers.submit(() -> {
	  invokeKeepAlive(manager);
	  return null;
	});
	try {
	  assertTrue(probe.executionStarted.await(1, TimeUnit.SECONDS));
	  Future<Connection> checkout = workers.submit(manager::getConnection);
	  Thread.sleep(200);
	  assertFalse(checkout.isDone(), "Connection was checked out during keep-alive");

	  probe.allowExecutionToFinish.countDown();
	  keepAlive.get(2, TimeUnit.SECONDS);
	  Connection checkedOut = checkout.get(2, TimeUnit.SECONDS);
	  manager.close(checkedOut);
	} finally {
	  probe.allowExecutionToFinish.countDown();
	  workers.shutdownNow();
	  manager.close();
	}
  }

  @Test
  void failedKeepAliveDiscardsAndReplacesConnection() throws Exception {
	AtomicInteger supplied = new AtomicInteger();
	Probe failing = new Probe(false, true);
	Probe healthy = new Probe(false, false);
	DefaultConnectionManager manager = manager(
			() -> supplied.getAndIncrement() == 0 ? failing.connection() : healthy.connection(),
			1
	);
	try {
	  Connection connection = manager.getConnection();
	  manager.close(connection);
	  makeIdle(manager, connection);

	  invokeKeepAlive(manager);

	  Connection replacement = manager.getConnection();
	  manager.close(replacement);
	  assertEquals(2, supplied.get());
	} finally {
	  manager.close();
	}
  }

  private static DefaultConnectionManager manager(
		  TrySupplier<Connection, SQLException> supplier,
		  int maximum
  ) {
	return new DefaultConnectionManager(
			supplier,
			maximum,
			Duration.ZERO,
			null,
			Duration.ofSeconds(1)
	);
  }

  @SuppressWarnings("unchecked")
  private static void makeIdle(DefaultConnectionManager manager, Connection connection) throws Exception {
	Field field = DefaultConnectionManager.class.getDeclaredField("obtainedConnections");
	field.setAccessible(true);
	Map<Connection, ConnectionMetadata> connections = (Map<Connection, ConnectionMetadata>) field.get(manager);
	connections.get(connection).lastTimeUsed.set(0L);
  }

  private static void invokeKeepAlive(DefaultConnectionManager manager) throws Exception {
	Method method = DefaultConnectionManager.class.getDeclaredMethod("executeQuery", String.class);
	method.setAccessible(true);
	method.invoke(manager, "SELECT 1");
  }

  private static Connection connection() {
	AtomicBoolean closed = new AtomicBoolean();
	return (Connection) Proxy.newProxyInstance(
			DefaultConnectionManagerTests.class.getClassLoader(),
			new Class<?>[]{Connection.class},
			(proxy, method, args) -> {
			  switch (method.getName()) {
				case "close":
				  closed.set(true);
				  return null;
				case "isClosed":
				  return closed.get();
				case "getAutoCommit":
				  return true;
				case "isReadOnly":
				  return false;
				case "getHoldability":
				  return 0;
				case "getTransactionIsolation":
				  return Connection.TRANSACTION_READ_COMMITTED;
				case "hashCode":
				  return System.identityHashCode(proxy);
				case "equals":
				  return proxy == args[0];
				case "toString":
				  return "connection@" + System.identityHashCode(proxy);
				default:
				  return null;
			  }
			}
	);
  }

  private static Connection connectionThatFailsThirdValidation(AtomicInteger closes) {
	AtomicInteger validations = new AtomicInteger();
	return (Connection) Proxy.newProxyInstance(
			DefaultConnectionManagerTests.class.getClassLoader(),
			new Class<?>[]{Connection.class},
			(proxy, method, args) -> {
			  switch (method.getName()) {
				case "close":
				  closes.incrementAndGet();
				  return null;
				case "isClosed":
				  if (validations.incrementAndGet() == 3) throw new SQLException("expected validation failure");
				  return false;
				case "getAutoCommit":
				  return true;
				case "isReadOnly":
				  return false;
				case "getHoldability":
				  return 0;
				case "getTransactionIsolation":
				  return Connection.TRANSACTION_READ_COMMITTED;
				case "hashCode":
				  return System.identityHashCode(proxy);
				case "equals":
				  return proxy == args[0];
				default:
				  return null;
			  }
			}
	);
  }

  private static Connection connectionThatFailsMetadataCapture(AtomicInteger closes) {
	return failingConnection(closes, true);
  }

  private static Connection connectionThatFailsInitialization(AtomicInteger closes) {
	return failingConnection(closes, false);
  }

  private static Connection failingConnection(AtomicInteger closes, boolean failMetadataCapture) {
	return (Connection) Proxy.newProxyInstance(
			DefaultConnectionManagerTests.class.getClassLoader(),
			new Class<?>[]{Connection.class},
			(proxy, method, args) -> {
			  switch (method.getName()) {
				case "close":
				  closes.incrementAndGet();
				  return null;
				case "isClosed":
				  return false;
				case "setAutoCommit":
				  throw new SQLException("expected initialization failure");
				case "getAutoCommit":
				  return true;
				case "isReadOnly":
				  return false;
				case "getHoldability":
				  if (failMetadataCapture) throw new SQLException("expected metadata failure");
				  return 0;
				case "getTransactionIsolation":
				  return Connection.TRANSACTION_READ_COMMITTED;
				case "hashCode":
				  return System.identityHashCode(proxy);
				case "equals":
				  return proxy == args[0];
				default:
				  return null;
			  }
			}
	);
  }

  private static final class Probe {

	private final AtomicBoolean closed = new AtomicBoolean();
	private final AtomicInteger executions = new AtomicInteger();
	private final AtomicInteger commits = new AtomicInteger();
	private final AtomicInteger autoCommitChanges = new AtomicInteger();
	private final AtomicInteger rollbacks = new AtomicInteger();
	private final CountDownLatch executionStarted = new CountDownLatch(1);
	private final CountDownLatch allowExecutionToFinish = new CountDownLatch(1);
	private final boolean blockExecution;
	private final boolean failExecution;

	private Probe(boolean blockExecution, boolean failExecution) {
	  this.blockExecution = blockExecution;
	  this.failExecution = failExecution;
	}

	private Connection connection() {
	  return (Connection) Proxy.newProxyInstance(
			  getClass().getClassLoader(),
			  new Class<?>[]{Connection.class},
			  (proxy, method, args) -> {
				switch (method.getName()) {
				  case "createStatement":
					return statement();
				  case "setAutoCommit":
					autoCommitChanges.incrementAndGet();
					return null;
				  case "rollback":
					rollbacks.incrementAndGet();
					return null;
				  case "commit":
					commits.incrementAndGet();
					return null;
				  case "close":
					closed.set(true);
					return null;
				  case "isClosed":
					return closed.get();
				  case "getAutoCommit":
					return true;
				  case "isReadOnly":
					return false;
				  case "getHoldability":
					return 0;
				  case "getTransactionIsolation":
					return Connection.TRANSACTION_READ_COMMITTED;
				  case "hashCode":
					return System.identityHashCode(proxy);
				  case "equals":
					return proxy == args[0];
				  default:
					return null;
				}
			  }
	  );
	}

	private Statement statement() {
	  return (Statement) Proxy.newProxyInstance(
			  getClass().getClassLoader(),
			  new Class<?>[]{Statement.class},
			  (proxy, method, args) -> {
				if ("execute".equals(method.getName())) {
				  executions.incrementAndGet();
				  executionStarted.countDown();
				  if (blockExecution) allowExecutionToFinish.await(2, TimeUnit.SECONDS);
				  if (failExecution) throw new SQLException("expected liveness failure");
				  return true;
				}
				if ("close".equals(method.getName())) return null;
				return null;
			  }
	  );
	}
  }

  private static final class UpdateProbe {

	private final AtomicBoolean closed = new AtomicBoolean();
	private final AtomicInteger executions = new AtomicInteger();
	private final AtomicInteger commits = new AtomicInteger();
	private final AtomicInteger rollbacks = new AtomicInteger();
	private final boolean failSecondExecution;

	private UpdateProbe(boolean failSecondExecution) {
	  this.failSecondExecution = failSecondExecution;
	}

	private Connection connection() {
	  return (Connection) Proxy.newProxyInstance(
			  getClass().getClassLoader(),
			  new Class<?>[]{Connection.class},
			  (proxy, method, args) -> {
				switch (method.getName()) {
				  case "prepareStatement":
					return statement();
				  case "commit":
					commits.incrementAndGet();
					return null;
				  case "rollback":
					rollbacks.incrementAndGet();
					return null;
				  case "close":
					closed.set(true);
					return null;
				  case "isClosed":
					return closed.get();
				  case "getAutoCommit":
					return true;
				  case "isReadOnly":
					return false;
				  case "getHoldability":
					return 0;
				  case "getTransactionIsolation":
					return Connection.TRANSACTION_READ_COMMITTED;
				  case "hashCode":
					return System.identityHashCode(proxy);
				  case "equals":
					return proxy == args[0];
				  default:
					return null;
				}
			  }
	  );
	}

	private Statement statement() {
	  return (Statement) Proxy.newProxyInstance(
			  getClass().getClassLoader(),
			  new Class<?>[]{java.sql.PreparedStatement.class},
			  (proxy, method, args) -> {
				if ("executeUpdate".equals(method.getName())) {
				  int execution = executions.incrementAndGet();
				  if (failSecondExecution && execution == 2) throw new SQLException("expected update failure");
				  return 1;
				}
				if ("isClosed".equals(method.getName())) return false;
				if ("close".equals(method.getName())) return null;
				return null;
			  }
	  );
	}
  }

}
