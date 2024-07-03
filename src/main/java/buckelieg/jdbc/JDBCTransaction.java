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
import buckelieg.jdbc.fn.TrySupplier;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static buckelieg.jdbc.Utils.newIntSequence;
import static buckelieg.jdbc.Utils.newSQLRuntimeException;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

@ParametersAreNonnullByDefault
final class JDBCTransaction implements Transaction {

  private Isolation isolation;

  private Predicate<Context> beforeCommitHandler;

  private Consumer<Context> commitHandler;

  private BiConsumer<? super Throwable, Context> errorHandler;

  private final Map<String, RSMeta.Column> metaCache;

  private final Supplier<String> txIdProvider;

  private final Supplier<ExecutorService> executorServiceSupplier;

  private final TrySupplier<Connection, SQLException> connectionProvider;

  private final TryConsumer<Connection, SQLException> connectionCloser;

  private final PrimitiveIterator.OfInt sequence = newIntSequence();

  JDBCTransaction(
		  Supplier<ExecutorService> executorServiceSupplier,
		  Supplier<String> txIdProvider,
		  Map<String, RSMeta.Column> metaCache,
		  TrySupplier<Connection, SQLException> connectionProvider,
		  TryConsumer<Connection, SQLException> connectionCloser) {
	this.executorServiceSupplier = executorServiceSupplier;
	this.txIdProvider = txIdProvider;
	this.connectionProvider = connectionProvider;
	this.connectionCloser = connectionCloser;
	this.metaCache = metaCache;
  }

  @Nonnull
  @Override
  public <T> T execute(TryBiFunction<Session, Context, T, ? extends Exception> transaction) {
	if (null == transaction) throw new NullPointerException("Transaction function must be provided");
	try {
	  return doInTransaction(transaction);
	} catch (Throwable throwable) {
	  throw newSQLRuntimeException(throwable);
	}
  }

  @Nonnull
  @Override
  public Transaction onBeforeCommit(Predicate<Context> beforeCommitHandler) {
	this.beforeCommitHandler = requireNonNull(beforeCommitHandler, "Transaction before commit handler must be provided");
	return this;
  }

  @Nonnull
  @Override
  public Transaction onCommit(Consumer<Context> commitHandler) {
	this.commitHandler = requireNonNull(commitHandler, "Transaction commit handler must be provided");
	return this;
  }

  @Nonnull
  @Override
  public Transaction onRollback(BiConsumer<? super Throwable, Context> rollbackHandler) {
	this.errorHandler = requireNonNull(rollbackHandler, "Transaction error handler must be provided");
	return this;
  }

  @Nonnull
  @Override
  public Transaction isolation(Isolation level) {
	this.isolation = requireNonNull(level, "Transaction isolation level must be provided");
	return this;
  }

  private <T> T doInTransaction(TryBiFunction<Session, Context, T, ? extends Exception> action) throws Exception {
	boolean autoCommit = true;
	Savepoint savepoint = null;
	boolean transactionSucceeded = true;
	Connection connection = connectionProvider.get();
	int isolationLevel = connection.getTransactionIsolation();
	T result;
	Context transactionContext = null;
	try {
	  autoCommit = connection.getAutoCommit();
	  connection.setAutoCommit(false);
	  final String username = connection.getMetaData().getUserName();

	  transactionContext = new Transaction.Context() {

		private final AtomicReference<String> txId = new AtomicReference<>();
		@Override
		public String username() {
		  return username;
		}

		@Override
		public String transactionId() {
		  return txId.updateAndGet(id -> id != null ? id : txIdProvider.get());
		}

	  };

	  savepoint = connection.setSavepoint(format("SAVEPOINT_%s@%s", sequence.nextInt(), transactionContext.transactionId()));
	  if (this.isolation != null && isolationLevel != this.isolation.level) {
		if (!connection.getMetaData().supportsTransactionIsolationLevel(isolation.level))
		  throw new SQLException(format("Unsupported transaction isolation level: '%s'", isolation.name()));
		connection.setTransactionIsolation(isolation.level);
	  }
	  result = action.apply(new Session(metaCache, () -> connection, TryConsumer.NOOP(), executorServiceSupplier), transactionContext);
	  if (null != beforeCommitHandler && !beforeCommitHandler.test(transactionContext)) {
		connection.rollback(savepoint);
		connection.releaseSavepoint(savepoint);
		transactionSucceeded = false;
		return null;
	  }
	  connection.commit();
	  return result;
	} catch (Exception e) {
	  transactionSucceeded = false;
	  connection.rollback(savepoint);
	  connection.releaseSavepoint(savepoint);
	  if (null != errorHandler) {
		errorHandler.accept(e, transactionContext);
		return null;
	  } else throw e;
	} finally {
	  connection.setAutoCommit(autoCommit);
	  connection.setTransactionIsolation(isolationLevel);
	  connectionCloser.accept(connection);
	  if (transactionSucceeded && null != commitHandler) commitHandler.accept(transactionContext);
	}
  }

}
