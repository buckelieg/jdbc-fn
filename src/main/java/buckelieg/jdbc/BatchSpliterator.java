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

import buckelieg.jdbc.fn.TryBiFunction;
import buckelieg.jdbc.fn.TryConsumer;
import buckelieg.jdbc.fn.TryTriConsumer;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static buckelieg.jdbc.Utils.entry;
import static buckelieg.jdbc.Utils.newSQLRuntimeException;
import static java.sql.JDBCType.*;

// TODO implement using CompletableFuture?
final class BatchSpliterator<T> implements Spliterator<T> {

  private ExecutorService executorService;

  private BlockingQueue<Map.Entry<List<T>, Integer>> processedBatchesQueue;

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private final AtomicReference<Throwable> exception = new AtomicReference<>();

  private final AtomicReference<Boolean> initializationResult = new AtomicReference<>();

  private final AtomicInteger batchIndex = new AtomicInteger();

  private final AtomicInteger batchCount = new AtomicInteger(-1);

  private Session session;

  private final AtomicBoolean executionStarted = new AtomicBoolean();

  private final AtomicBoolean cancellationRequested = new AtomicBoolean();

  private final List<Future<?>> submittedTasks = new ArrayList<>();

  private final AtomicBoolean isClosing = new AtomicBoolean();

  private final TryBiFunction<ValueReader, Integer, T, SQLException> mapper;

  private final TryTriConsumer<List<T>, Session, Integer, ? extends Exception> batchProcessor;

  private final SelectQuery selectQuery;

  private int size;

  BatchSpliterator(
		  SelectQuery selectQuery,
		  TryBiFunction<ValueReader, Integer, T, SQLException> mapper,
		  TryTriConsumer<List<T>, Session, Integer, ? extends Exception> batchProcessor,
		  int size) {
	this.selectQuery = selectQuery;
	this.mapper = mapper;
	this.batchProcessor = batchProcessor;
	this.size = size;
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
	return IMMUTABLE;
  }

  @Override
  public boolean tryAdvance(Consumer<? super T> action) {
	if (!init()) return false;
	if (batchCount.get() > 0) {
	  for (T item : next())
		action.accept(item);
	  return true;
	} else return false;
  }

  private List<T> next() {
	try {
	  int count = batchCount.getAndDecrement();
	  Map.Entry<List<T>, Integer> batch = 0 == count ? entry(Collections.emptyList(), 0) : processedBatchesQueue.take();
	  return batch.getKey();
	} catch (InterruptedException e) {
	  Thread.currentThread().interrupt();
	  throw new RuntimeException(e);
	}
  }

  void close() {
	if (!isClosing.getAndSet(true)) {
	  cancellationRequested.compareAndSet(false, true);
	  // gracefully closing query:
	  // here we have to drain all data from all tasks which are be pending or running at the moment and might use a session (connection)
	  do {
		next(); // TODO are there any chances to short circuit these out?
	  } while (!submittedTasks.stream().allMatch(Future::isDone));
	  selectQuery.finisher.run();
	  selectQuery.close();
	  if (null != exception.get())
		throw newSQLRuntimeException(exception.get());
	}
  }

  private boolean init() {
	Boolean result = initializationResult.get();
	if (null != result) return result;
	if (!isInitialized.getAndSet(true)) {
	  try {
		selectQuery.statement = selectQuery.prepareStatement();
		selectQuery.resultSet = selectQuery.doExecute(selectQuery.statement);
		if (selectQuery.resultSet != null) {
		  selectQuery.meta = new RSMeta(selectQuery.getConnection()::getMetaData, selectQuery.resultSet::getMetaData, selectQuery.metaCache);
		  session = new Session(selectQuery.metaCache, selectQuery::getConnection, TryConsumer.NOOP(), selectQuery.executorServiceSupplier);
		  selectQuery.wrapper = ValueGetters.reader(selectQuery.meta, selectQuery.resultSet);
		  size = selectQuery.meta.containsAny(LONGVARBINARY, LONGNVARCHAR, LONGVARCHAR, BLOB, CLOB, NCLOB) ? 1 : size;
		  processedBatchesQueue = new ArrayBlockingQueue<>(size);
		  executorService = selectQuery.executorServiceSupplier.get();
		} else {
		  selectQuery.finisher.run();
		  initializationResult.set(false);
		  return false;
		}
	  } catch (SQLException e) {
		exception.compareAndSet(null, e);
		initializationResult.set(false);
		return false;
	  }
	  executorService.execute(() -> {
		try {
		  int index = 0;
		  List<T> batch = new ArrayList<>(size);
		  while (selectQuery.resultSet.next() && !cancellationRequested.get()) {
			if (0 != index && 0 == index % size && !cancellationRequested.get()) {
			  index = 0;
			  dispatch(new ArrayList<>(batch), batchIndex.incrementAndGet());
			  batch = new ArrayList<>(size);
			}
			batch.add(mapper.apply(selectQuery.wrapper, selectQuery.currentResultSetNumber.get()));
			index++;
		  }
		  if (!batch.isEmpty() && !cancellationRequested.get()) {
			dispatch(new ArrayList<>(batch), batchIndex.incrementAndGet());
		  }
		  batchCount.compareAndSet(-1, batchIndex.get());
		  executionStarted.compareAndSet(false, true);
		} catch (SQLException e) {
		  exception.compareAndSet(null, e);
		  cancellationRequested.set(true);
		}
	  });
	}
	do {
	  if (cancellationRequested.get()) {
		initializationResult.set(false);
		return false;
	  }
	} while (!executionStarted.get());
	initializationResult.set(true);
	return true;
  }

  private void dispatch(List<T> batch, int index) {
	if (!cancellationRequested.get()) {
	  submittedTasks.add(executorService.submit(() -> {
		try {
		  batchProcessor.accept(batch, session, index);
		  processedBatchesQueue.put(entry(batch, index));
		} catch (InterruptedException e) {
		  Thread.currentThread().interrupt();
		  exception.compareAndSet(null, e);
		} catch (Exception e) {
		  exception.compareAndSet(null, e);
		  cancellationRequested.set(true);
		}
	  }));
	}
  }
}
