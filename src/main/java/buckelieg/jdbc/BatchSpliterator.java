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

import buckelieg.fn.TryBiConsumer;
import buckelieg.fn.TryBiFunction;
import buckelieg.fn.TryTriConsumer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static buckelieg.jdbc.Utils.newSQLRuntimeException;
import static java.lang.Math.max;
import static java.sql.JDBCType.BLOB;
import static java.sql.JDBCType.CLOB;
import static java.sql.JDBCType.LONGNVARCHAR;
import static java.sql.JDBCType.LONGVARBINARY;
import static java.sql.JDBCType.LONGVARCHAR;
import static java.sql.JDBCType.NCLOB;
import static java.util.Objects.requireNonNull;

final class BatchSpliterator<T> implements Spliterator<T> {

  private final AtomicBoolean initialized = new AtomicBoolean();

  private final AtomicBoolean closing = new AtomicBoolean();

  private final AtomicBoolean finished = new AtomicBoolean();

  private final AtomicBoolean ordered = new AtomicBoolean(true);

  private final TryBiFunction<ValueReader, Integer, T, SQLException> mapper;

  private final TryTriConsumer<List<T>, Session, Integer, ? extends Exception> batchProcessor;

  private final SelectQuery selectQuery;

  private final Deque<BatchTask<T>> inFlight = new ArrayDeque<>();

  private final BlockingQueue<BatchTask<T>> completed = new LinkedBlockingQueue<>();

  private final AtomicReference<Throwable> processingFailure = new AtomicReference<>();

  private List<T> currentBatch = Collections.emptyList();

  private int currentItem;

  private int batchIndex;

  private int size;

  private int concurrency;

  private boolean exhausted;

  private boolean sourceExhausted;

  private boolean executed;

  BatchSpliterator(
		  SelectQuery selectQuery,
		  TryBiFunction<ValueReader, Integer, T, SQLException> mapper,
		  TryTriConsumer<List<T>, Session, Integer, ? extends Exception> batchProcessor,
		  int size,
		  int concurrency) {
	this.selectQuery = selectQuery;
	this.mapper = mapper;
	this.batchProcessor = batchProcessor;
	this.size = size;
	this.concurrency = max(1, concurrency);
  }

  static int defaultConcurrency() {
	return max(1, Runtime.getRuntime().availableProcessors());
  }

  void unordered() {
	ordered.set(false);
  }

  @Override
  public String toString() {
	return selectQuery.toString();
  }

  @Override
  public Spliterator<T> trySplit() {
	return null;
  }

  @Override
  public long estimateSize() {
	return Long.MAX_VALUE;
  }

  @Override
  public int characteristics() {
	return IMMUTABLE | (ordered.get() ? ORDERED : 0);
  }

  @Override
  public boolean tryAdvance(Consumer<? super T> action) {
	requireNonNull(action, "Action must be provided");
	if (closing.get() || exhausted) return false;
	try {
	  if (!initialize()) return false;

	  while (currentItem >= currentBatch.size()) {
		if (!fetchAndProcessNextBatch()) {
		  exhausted = true;
		  return false;
		}
	  }

	  action.accept(currentBatch.get(currentItem++));
	  return true;
	} catch (Throwable failure) {
	  selectQuery.markFailed();
	  if (failure instanceof Error) throw (Error) failure;
	  throw newSQLRuntimeException(failure);
	}
  }

  private boolean initialize() throws Exception {
	if (initialized.get()) return null != selectQuery.resultSet;
	if (!initialized.compareAndSet(false, true)) return null != selectQuery.resultSet;

	boolean initialized = selectQuery.initializeResultSet();
	executed = true;
	if (!initialized) return false;
	if (selectQuery.meta.containsAny(LONGVARBINARY, LONGNVARCHAR, LONGVARCHAR, BLOB, CLOB, NCLOB)) {
	  size = 1;
	  concurrency = 1;
	}
	return true;
  }

  private boolean fetchAndProcessNextBatch() throws Throwable {
	fillPipeline();
	if (inFlight.isEmpty()) return false;

	BatchTask<T> next;
	if (ordered.get()) next = inFlight.removeFirst();
	else {
	  try {
		next = completed.take();
	  } catch (InterruptedException e) {
		Thread.currentThread().interrupt();
		throw e;
	  }
	  inFlight.remove(next);
	}
	await(next.future);
	currentBatch = next.batch;
	currentItem = 0;
	return true;
  }

  private void fillPipeline() throws SQLException {
	while (!sourceExhausted && inFlight.size() < concurrency) {
	  List<T> batch = readNextBatch();
	  if (batch.isEmpty()) return;
	  submit(batch, ++batchIndex);
	}
  }

  private List<T> readNextBatch() throws SQLException {
	List<T> batch = new ArrayList<>(size);
	while (batch.size() < size) {
	  if (!selectQuery.resultSet.next()) {
		sourceExhausted = true;
		break;
	  }
	  batch.add(mapper.apply(selectQuery.wrapper, selectQuery.currentResultSetNumber.get()));
	}
	return batch;
  }

  private void submit(List<T> batch, int index) {
	BatchTask<T> task = new BatchTask<>(batch);
	task.future = selectQuery.executorService.submit(() -> {
	  try {
		if (closing.get()) return;
		process(batch, index);
	  } catch (Throwable failure) {
		processingFailure.compareAndSet(null, failure);
		if (failure instanceof Error) throw (Error) failure;
		if (failure instanceof RuntimeException) throw (RuntimeException) failure;
		throw new BatchProcessingException(failure);
	  } finally {
		if (!ordered.get()) completed.offer(task);
	  }
	});
	inFlight.addLast(task);
  }

  private void await(Future<?> future) throws Throwable {
	try {
	  future.get();
	} catch (InterruptedException e) {
	  Thread.currentThread().interrupt();
	  throw e;
	} catch (ExecutionException e) {
	  Throwable failure = e.getCause();
	  if (failure instanceof BatchProcessingException && null != failure.getCause()) failure = failure.getCause();
	  throw failure;
	}
  }

  private void process(List<T> batch, int index) throws Throwable {
	AtomicReference<Connection> processingConnection = new AtomicReference<>();
	Session processingSession = new Session(
			selectQuery.metaCache,
			() -> {
			  Connection connection = processingConnection.get();
			  if (null == connection) {
				connection = selectQuery.processingConnectionSupplier.get();
				processingConnection.set(connection);
			  }
			  return connection;
			},
			TryBiConsumer.NOOP(),
			selectQuery.processingConnectionSupplier,
			selectQuery.processingConnectionCloser,
			selectQuery.executorService
	);

	boolean successful = false;
	Throwable failure = null;
	try {
	  batchProcessor.accept(batch, processingSession, index);
	  successful = true;
	} catch (Throwable e) {
	  failure = e;
	  throw e;
	} finally {
	  Connection connection = processingConnection.getAndSet(null);
	  if (null != connection) {
		try {
		  selectQuery.processingConnectionCloser.accept(connection, successful);
		} catch (Throwable closeFailure) {
		  if (null != failure) failure.addSuppressed(closeFailure);
		  else throw closeFailure;
		}
	  }
	}
  }

  void close() {
	if (!closing.compareAndSet(false, true)) return;
	try {
	  awaitOutstanding();
	  finish();
	} catch (Throwable failure) {
	  selectQuery.markFailed();
	  if (failure instanceof Error) throw (Error) failure;
	  throw newSQLRuntimeException(failure);
	} finally {
	  selectQuery.close();
	}
  }

  private void awaitOutstanding() throws Throwable {
	Throwable firstFailure = null;
	boolean interrupted = false;
	for (BatchTask<T> task : inFlight) {
	  boolean complete = false;
	  while (!complete) {
		try {
		  await(task.future);
		  complete = true;
		} catch (InterruptedException failure) {
		  interrupted = true;
		  Thread.interrupted();
		  if (null == firstFailure) firstFailure = failure;
		} catch (CancellationException ignored) {
		  // The executor cancelled the task before it obtained a processing connection.
		  complete = true;
		} catch (Throwable failure) {
		  if (null == firstFailure) firstFailure = failure;
		  else if (firstFailure != failure) firstFailure.addSuppressed(failure);
		  complete = true;
		}
	  }
	}
	inFlight.clear();
	if (interrupted) Thread.currentThread().interrupt();
	Throwable asynchronousFailure = processingFailure.get();
	if (null == firstFailure) firstFailure = asynchronousFailure;
	else if (null != asynchronousFailure && firstFailure != asynchronousFailure) {
	  firstFailure.addSuppressed(asynchronousFailure);
	}
	if (null != firstFailure) throw firstFailure;
  }

  private void finish() {
	if (executed && finished.compareAndSet(false, true)) selectQuery.finisher.run();
  }

  private static final class BatchTask<T> {

	private final List<T> batch;
	private Future<?> future;

	private BatchTask(List<T> batch) {
	  this.batch = batch;
	}
  }

  private static final class BatchProcessingException extends RuntimeException {

	private BatchProcessingException(Throwable cause) {
	  super(cause);
	}
  }

}
