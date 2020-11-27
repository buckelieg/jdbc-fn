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

import buckelieg.jdbc.fn.TryAction;
import buckelieg.jdbc.fn.TryConsumer;
import buckelieg.jdbc.fn.TrySupplier;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static buckelieg.jdbc.Utils.newSQLRuntimeException;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("unchecked")
abstract class AbstractQuery<S extends Statement> implements Query {

    protected final Executor conveyor;
    protected S statement;
    protected final String query;
    private final String sqlString;
    protected final TrySupplier<Connection, SQLException> connectionSupplier;
    protected boolean skipWarnings = true;
    protected final boolean isPrepared;
    protected int timeout;
    protected TimeUnit unit = TimeUnit.SECONDS;
    protected boolean isPoolable;
    protected boolean isEscaped = true;
    protected boolean poolable;
    protected boolean escapeProcessing;
    protected final Object[] params;
    protected Connection connectionInUse;

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    AbstractQuery(Executor conveyor, TrySupplier<Connection, SQLException> connectionSupplier, String query, Object... params) {
        this.conveyor = conveyor;
        this.query = query;
        this.connectionSupplier = connectionSupplier;
        this.params = params;
        this.isPrepared = params != null && params.length != 0;
        this.sqlString = asSQL(query, params);
    }

    @Override
    public void close() {
        if (statement != null) {
            try {
                if(!statement.isClosed()) {
                    statement.close(); // by JDBC spec: subsequently closes all result sets opened by this statement
                }
            } catch (SQLException e) {
                throw newSQLRuntimeException(e);
            }
        }
    }

    final void setTimeout() {
        setStatementParameter(statement -> statement.setQueryTimeout(max((int) requireNonNull(unit, "Time Unit must be provided").toSeconds(timeout), 0)));
    }

    final void setPoolable() {
        setStatementParameter(statement -> statement.setPoolable(poolable));
    }

    final void setEscapeProcessing() {
        setStatementParameter(statement -> statement.setEscapeProcessing(escapeProcessing));
    }

    final <Q extends Query> Q setTimeout(int timeout, TimeUnit unit) {
        this.timeout = timeout;
        this.unit = requireNonNull(unit, "Time unit must be provided");
        return (Q) this;
    }

    final <Q extends Query> Q setPoolable(boolean poolable) {
        this.poolable = poolable;
        return (Q) this;
    }

    final <Q extends Query> Q setSkipWarnings(boolean skipWarnings) {
        this.skipWarnings = skipWarnings;
        return (Q) this;
    }

    final <Q extends Query> Q setEscapeProcessing(boolean escapeProcessing) {
        this.escapeProcessing = escapeProcessing;
        return (Q) this;
    }

    final <Q extends Query> Q log(Consumer<String> printer) {
        requireNonNull(printer, "Printer must be provided");
        conveyor.execute(() -> printer.accept(sqlString));
        return (Q) this;
    }

    final <O> O jdbcTry(TrySupplier<O, SQLException> supplier) {
        O result = null;
        try {
            result = supplier.get();
            if (!skipWarnings && statement.getWarnings() != null) {
                throw statement.getWarnings();
            }
        } catch (AbstractMethodError ame) {
            // ignore this possible vendor-specific JDBC driver's error.
        } catch (SQLException e) {
            close();
            throw newSQLRuntimeException(e);
        } catch (Exception e) {
            if (!e.getClass().equals(SQLRuntimeException.class)) {
                close();
                throw new RuntimeException(e);
            } else {
                throw e;
            }
        }
        return result;
    }

    final void jdbcTry(TryAction<SQLException> action) {
        try {
            action.doTry();
            if (!skipWarnings && statement.getWarnings() != null) {
                throw statement.getWarnings();
            }
        } catch (AbstractMethodError ame) {
            // ignore this possible vendor-specific JDBC driver's error.
        } catch (SQLException e) {
            close();
            throw newSQLRuntimeException(e);
        } catch (Exception e) {
            if (!e.getClass().equals(SQLRuntimeException.class)) {
                close();
                throw new RuntimeException(e);
            } else {
                throw e;
            }
        }
    }

    final void setStatementParameter(TryConsumer<S, SQLException> action) {
        jdbcTry(() -> action.accept(statement));
    }

    final synchronized <O> O runSync(TrySupplier<O, SQLException> action) {
        try {
            lock.lock();
            while (isRunning.get()) {
                condition.await();
            }
            isRunning.set(true);
            return jdbcTry(action);
        } catch (Exception e) {
            throw newSQLRuntimeException(e);
        } finally {
            isRunning.set(false);
            condition.signalAll();
            lock.unlock();
        }
    }

    String asSQL(String query, Object... params) {
        return Utils.asSQL(query, params);
    }

    @Nonnull
    @Override
    public final String asSQL() {
        return sqlString;
    }

    @Override
    public String toString() {
        return asSQL();
    }
}
