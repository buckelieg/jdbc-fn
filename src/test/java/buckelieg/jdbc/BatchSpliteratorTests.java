package buckelieg.jdbc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchSpliteratorTests {

  @Test
  void processesBatchesOnWorkersAndEmitsThemInSourceOrder() {
	Probe probe = new Probe(5);
	ExecutorService executor = Executors.newFixedThreadPool(2);
	CountDownLatch firstTwoProcessors = new CountDownLatch(2);
	AtomicInteger activeProcessors = new AtomicInteger();
	AtomicInteger maxActiveProcessors = new AtomicInteger();
	try {
	  Thread callingThread = Thread.currentThread();
	  Stream<Integer> stream = probe.query(executor)
			  .forBatch((reader, resultSetIndex) -> probe.currentRow())
			  .size(2)
			  .concurrency(2)
			  .execute((batch, session, batchIndex) -> {
				assertFalse(callingThread.equals(Thread.currentThread()));
				int active = activeProcessors.incrementAndGet();
				maxActiveProcessors.updateAndGet(current -> Math.max(current, active));
				firstTwoProcessors.countDown();
				try {
				  assertTrue(firstTwoProcessors.await(2, TimeUnit.SECONDS));
				  if (batchIndex == 1) Thread.sleep(40);
				  batch.add(-batchIndex);
				} finally {
				  activeProcessors.decrementAndGet();
				}
			  });

	  assertEquals(Arrays.asList(1, 2, -1, 3, 4, -2, 5, -3), stream.parallel().collect(toList()));
	  assertEquals(2, maxActiveProcessors.get());
	  assertEquals(Boolean.TRUE, probe.outcome.get());
	  assertFalse(probe.statementOpen.get());
	} finally {
	  executor.shutdownNow();
	}
  }

  @Test
  void unorderedStreamEmitsBatchesInCompletionOrder() {
	Probe probe = new Probe(4);
	ExecutorService executor = Executors.newFixedThreadPool(2);
	CountDownLatch processorsStarted = new CountDownLatch(2);
	CountDownLatch releaseFirstProcessor = new CountDownLatch(1);
	try {
	  List<Integer> result = probe.query(executor)
			  .forBatch((reader, resultSetIndex) -> probe.currentRow())
			  .size(2)
			  .concurrency(2)
			  .execute((batch, session, batchIndex) -> {
				processorsStarted.countDown();
				assertTrue(processorsStarted.await(2, TimeUnit.SECONDS));
				if (batchIndex == 1) assertTrue(releaseFirstProcessor.await(2, TimeUnit.SECONDS));
				batch.add(-batchIndex);
			  })
			  .unordered()
			  .parallel()
			  .peek(item -> {
				if (item == 3) releaseFirstProcessor.countDown();
			  })
			  .collect(toList());

	  assertEquals(Arrays.asList(3, 4, -2, 1, 2, -1), result);
	  assertEquals(Boolean.TRUE, probe.outcome.get());
	  assertFalse(probe.statementOpen.get());
	} finally {
	  executor.shutdownNow();
	}
  }

  @Test
  void defaultsConcurrencyToAvailableProcessors() {
	assertEquals(
			Math.max(1, Runtime.getRuntime().availableProcessors()),
			BatchSpliterator.defaultConcurrency()
	);
  }

  @Test
  void shortCircuitProcessesOnlyTheCurrentBatch() {
	Probe probe = new Probe(5);
	ExecutorService executor = Executors.newSingleThreadExecutor();
	try {
	  Optional<Integer> first = probe.query(executor)
			  .forBatch((reader, resultSetIndex) -> probe.currentRow())
			  .size(2)
			  .concurrency(1)
			  .execute((batch, session, batchIndex) -> probe.process(batchIndex))
			  .findFirst();

	  assertEquals(Integer.valueOf(1), first.orElse(null));
	  assertEquals(Collections.singletonList(2), probe.rowsFetchedWhenProcessed);
	  assertEquals(2, probe.rowsFetched.get());
	  assertEquals(Boolean.TRUE, probe.outcome.get());
	  assertFalse(probe.statementOpen.get());
	} finally {
	  executor.shutdownNow();
	}
  }

  @Test
  void processorFailureStopsFetchingAndReportsFailedOutcome() {
	Probe probe = new Probe(5);
	ExecutorService executor = Executors.newSingleThreadExecutor();
	try {
	  Stream<Integer> stream = probe.query(executor)
			  .forBatch((reader, resultSetIndex) -> probe.currentRow())
			  .size(2)
			  .concurrency(1)
			  .execute((batch, session, batchIndex) -> {
				probe.process(batchIndex);
				throw new SQLException("expected processor failure");
			  });

	  assertThrows(SQLRuntimeException.class, () -> stream.collect(toList()));
	  assertEquals(2, probe.rowsFetched.get());
	  assertEquals(Boolean.FALSE, probe.outcome.get());
	  assertFalse(probe.statementOpen.get());
	} finally {
	  executor.shutdownNow();
	}
  }

  @Test
  void processorFailureWaitsForOtherActiveBatchesBeforeClosing() {
	Probe probe = new Probe(4);
	ExecutorService executor = Executors.newFixedThreadPool(2);
	CountDownLatch processorsStarted = new CountDownLatch(2);
	AtomicBoolean secondProcessorCompleted = new AtomicBoolean();
	try {
	  Stream<Integer> stream = probe.query(executor)
			  .forBatch((reader, resultSetIndex) -> probe.currentRow())
			  .size(2)
			  .concurrency(2)
			  .execute((batch, session, batchIndex) -> {
				processorsStarted.countDown();
				assertTrue(processorsStarted.await(2, TimeUnit.SECONDS));
				if (batchIndex == 1) throw new SQLException("expected processor failure");
				Thread.sleep(40);
				secondProcessorCompleted.set(true);
			  });

	  assertThrows(SQLRuntimeException.class, () -> stream.collect(toList()));
	  assertTrue(secondProcessorCompleted.get());
	  assertEquals(Boolean.FALSE, probe.outcome.get());
	  assertFalse(probe.statementOpen.get());
	} finally {
	  executor.shutdownNow();
	}
  }

  @Test
  void parallelSessionsUseSeparateConnectionsAndCompleteOneTransactionPerBatch() {
	Probe probe = new Probe(4);
	ExecutorService executor = Executors.newFixedThreadPool(2);
	AtomicInteger leases = new AtomicInteger();
	List<Boolean> outcomes = Collections.synchronizedList(new ArrayList<>());
	CountDownLatch sessionsReady = new CountDownLatch(2);
	try {
	  Stream<Integer> stream = probe.query(executor, leases, outcomes)
			  .forBatch((reader, resultSetIndex) -> probe.currentRow())
			  .size(2)
			  .concurrency(2)
			  .execute((batch, session) -> {
				assertEquals(
						0L,
						session.select("SELECT VALUE FROM TEST")
								.streaming(Select.Streaming.PREFERRED)
								.execute(reader -> 1)
								.count()
				);
				sessionsReady.countDown();
				assertTrue(sessionsReady.await(2, TimeUnit.SECONDS));
			  });

	  assertEquals(Arrays.asList(1, 2, 3, 4), stream.collect(toList()));
	  assertEquals(2, leases.get());
	  assertEquals(Arrays.asList(Boolean.TRUE, Boolean.TRUE), outcomes);
	  assertEquals(Boolean.TRUE, probe.outcome.get());
	} finally {
	  executor.shutdownNow();
	}
  }

  private static Connection emptyConnection() {
	return (Connection) Proxy.newProxyInstance(
			BatchSpliteratorTests.class.getClassLoader(),
			new Class<?>[]{Connection.class},
			(proxy, method, args) -> {
			  switch (method.getName()) {
				case "prepareStatement":
				  return emptyStatement();
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

  private static PreparedStatement emptyStatement() {
	ResultSetMetaData metadata = emptyMetadata();
	return (PreparedStatement) Proxy.newProxyInstance(
			BatchSpliteratorTests.class.getClassLoader(),
			new Class<?>[]{PreparedStatement.class},
			(proxy, method, args) -> {
			  switch (method.getName()) {
				case "getMetaData":
				  return metadata;
				case "executeQuery":
				  return emptyResultSet(metadata);
				case "isClosed":
				  return false;
				case "getMoreResults":
				  return false;
				default:
				  return null;
			  }
			}
	);
  }

  private static ResultSet emptyResultSet(ResultSetMetaData metadata) {
	return (ResultSet) Proxy.newProxyInstance(
			BatchSpliteratorTests.class.getClassLoader(),
			new Class<?>[]{ResultSet.class},
			(proxy, method, args) -> {
			  switch (method.getName()) {
				case "getMetaData":
				  return metadata;
				case "next":
				  return false;
				default:
				  return null;
			  }
			}
	);
  }

  private static ResultSetMetaData emptyMetadata() {
	return (ResultSetMetaData) Proxy.newProxyInstance(
			BatchSpliteratorTests.class.getClassLoader(),
			new Class<?>[]{ResultSetMetaData.class},
			(proxy, method, args) -> "getColumnCount".equals(method.getName()) ? 0 : null
	);
  }

  private static final class Probe {

	private final int rowCount;
	private final AtomicInteger cursor = new AtomicInteger();
	private final AtomicInteger rowsFetched = new AtomicInteger();
	private final AtomicBoolean processing = new AtomicBoolean();
	private final AtomicBoolean statementOpen = new AtomicBoolean();
	private final AtomicReference<Boolean> outcome = new AtomicReference<>();
	private final List<Integer> rowsFetchedWhenProcessed = new ArrayList<>();

	private Probe(int rowCount) {
	  this.rowCount = rowCount;
	}

	private SelectQuery query(ExecutorService executor) {
	  Connection connection = connection();
	  SelectQuery query = new SelectQuery(
			  new ConcurrentHashMap<>(),
			  () -> connection,
			  (ignored, successful) -> outcome.set(successful),
			  executor,
			  "SELECT VALUE FROM TEST"
	  );
	  query.streaming(Select.Streaming.PREFERRED);
	  return query;
	}

	private SelectQuery query(
			ExecutorService executor,
			AtomicInteger leases,
			List<Boolean> outcomes) {
	  Connection sourceConnection = connection();
	  SelectQuery query = new SelectQuery(
			  new ConcurrentHashMap<>(),
			  () -> sourceConnection,
			  (ignored, successful) -> outcome.set(successful),
			  () -> {
				leases.incrementAndGet();
				return emptyConnection();
			  },
			  (connection, successful) -> outcomes.add(successful),
			  executor,
			  "SELECT VALUE FROM TEST"
	  );
	  query.streaming(Select.Streaming.PREFERRED);
	  return query;
	}

	private int currentRow() {
	  return cursor.get();
	}

	private void process(int batchIndex) throws InterruptedException {
	  assertTrue(processing.compareAndSet(false, true));
	  try {
		rowsFetchedWhenProcessed.add(rowsFetched.get());
		Thread.sleep(20);
	  } finally {
		processing.set(false);
	  }
	}

	private Connection connection() {
	  return (Connection) Proxy.newProxyInstance(
			  getClass().getClassLoader(),
			  new Class<?>[]{Connection.class},
			  (proxy, method, args) -> {
				switch (method.getName()) {
				  case "prepareStatement":
					return statement();
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

	private PreparedStatement statement() {
	  statementOpen.set(true);
	  ResultSet resultSet = resultSet();
	  return (PreparedStatement) Proxy.newProxyInstance(
			  getClass().getClassLoader(),
			  new Class<?>[]{PreparedStatement.class},
			  (proxy, method, args) -> {
				switch (method.getName()) {
				  case "executeQuery":
					return resultSet;
				  case "getMetaData":
					return metadata();
				  case "isClosed":
					return !statementOpen.get();
				  case "close":
					statementOpen.set(false);
					return null;
				  default:
					return null;
				}
			  }
	  );
	}

	private ResultSet resultSet() {
	  ResultSetMetaData metadata = metadata();
	  return (ResultSet) Proxy.newProxyInstance(
			  getClass().getClassLoader(),
			  new Class<?>[]{ResultSet.class},
			  (proxy, method, args) -> {
				switch (method.getName()) {
				  case "next":
					if (cursor.get() >= rowCount) return false;
					cursor.incrementAndGet();
					rowsFetched.incrementAndGet();
					return true;
				  case "getMetaData":
					return metadata;
				  default:
					return null;
				}
			  }
	  );
	}

	private ResultSetMetaData metadata() {
	  return (ResultSetMetaData) Proxy.newProxyInstance(
			  getClass().getClassLoader(),
			  new Class<?>[]{ResultSetMetaData.class},
			  (proxy, method, args) -> {
				switch (method.getName()) {
				  case "getColumnCount":
					return 1;
				  case "getColumnType":
					return Types.INTEGER;
				  case "getColumnClassName":
					return Integer.class.getName();
				  case "getColumnName":
				  case "getColumnLabel":
					return "VALUE";
				  case "getCatalogName":
				  case "getSchemaName":
				  case "getTableName":
					return "";
				  case "isNullable":
					return ResultSetMetaData.columnNullable;
				  default:
					return null;
				}
			  }
	  );
	}
  }

}
