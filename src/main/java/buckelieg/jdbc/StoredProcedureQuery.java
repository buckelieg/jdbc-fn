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
import buckelieg.jdbc.fn.TrySupplier;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

@NotThreadSafe
@ParametersAreNonnullByDefault
final class StoredProcedureQuery extends SelectQuery implements StoredProcedure {

  StoredProcedureQuery(
		  Map<String, RSMeta.Column> metaCache,
		  TrySupplier<Connection, SQLException> connectionSupplier,
		  TryConsumer<Connection, ? extends Throwable> connectionConsumer,
		  Supplier<ExecutorService> executorServiceSupplier,
		  String query, P<?>... params) {
	super(metaCache, connectionSupplier, connectionConsumer, executorServiceSupplier, query, (Object[]) params);
  }

  @Nonnull
  @Override
  public <T> Select call(TryFunction<ValueReader, T, SQLException> mapper, Consumer<T> consumer) {
	if (null == mapper) throw new NullPointerException("Mapper must be provided");
	if (null == consumer) throw new NullPointerException("Consumer must be provided");
	this.finisher = () -> {
	  if (mapper != null && consumer != null && isPrepared) {
		try {
		  consumer.accept(mapper.apply(ValueGetters.reader(meta, (CallableStatement) statement)));
		} catch (SQLException e) {
		  throw new RuntimeException(e);
		}
	  }
	};
	return this;
  }

  @Nonnull
  @Override
  public StoredProcedure timeout(int timeout) {
	return (StoredProcedure) super.timeout(timeout);
  }

  @Nonnull
  @Override
  public StoredProcedure skipWarnings(boolean skipWarnings) {
	return (StoredProcedure) super.skipWarnings(skipWarnings);
  }

  @Nonnull
  @Override
  public StoredProcedure print(Consumer<String> printer) {
	return (StoredProcedure) super.print(printer);
  }

  @SuppressWarnings("unchecked")
  @Nonnull
  @Override
  public Stream<Map<String, Object>> execute() {
	AtomicReference<TryFunction<ValueReader, Map<String, Object>, SQLException>> mapper = new AtomicReference<>();
	return execute((rs, i) -> {
	  if (currentResultSetNumber.get() != i || null == mapper.get()) mapper.set(JDBCDefaults.defaultMapper());
	  return mapper.get().apply(rs);
	});
  }

  @Override
  protected ResultSet doExecute(Statement statement) throws SQLException {
	configureStatement(statement);
	return (isPrepared ? ((CallableStatement) statement).execute() : statement.execute(query)) ? statement.getResultSet() : null;
  }

  @Override
  protected Statement prepareStatement() throws SQLException {
	if (isPrepared) {
	  CallableStatement callableStatement = getConnection().prepareCall(query);
	  meta = new RSMeta(getConnection()::getMetaData, callableStatement::getMetaData, metaCache);
	  ValueWriter writer = ValueSetters.writer(meta, callableStatement);
	  for (int i = 1; i <= params.length; i++) {
		P<?> p = (P<?>) params[i - 1];
		if (p.isOut() || p.isInOut()) {
		  SQLType type = requireNonNull(p.type(), format("Parameter '%s' must have SQLType set", p));
		  try {
			callableStatement.registerOutParameter(i, type);
		  } catch (SQLFeatureNotSupportedException e) {
			// fallback to previous version of JDBC
			callableStatement.registerOutParameter(i, type.getVendorTypeNumber());
		  }
		}
		if (p.isIn() || p.isInOut()) JDBCDefaults.writer(p.type()).accept(writer, i, p.value());
	  }
	  return callableStatement;
	}
	return getConnection().createStatement();
  }

}
