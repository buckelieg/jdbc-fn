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

import buckelieg.fn.TryBiConsumer;
import buckelieg.fn.TryFunction;
import buckelieg.fn.TrySupplier;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLType;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

final class StoredProcedureQuery extends SelectQuery implements StoredProcedure {

  StoredProcedureQuery(
		  Map<String, MetadataImpl.Column> metaCache,
		  TrySupplier<Connection, SQLException> connectionSupplier,
		  TryBiConsumer<Connection, Boolean, ? extends Throwable> connectionConsumer,
		  TrySupplier<Connection, SQLException> processingConnectionSupplier,
		  TryBiConsumer<Connection, Boolean, ? extends Throwable> processingConnectionCloser,
		  ExecutorService executorService,
		  String query, P<?>... params) {
	super(
			metaCache,
			connectionSupplier,
			connectionConsumer,
			processingConnectionSupplier,
			processingConnectionCloser,
			executorService,
			query,
			(Object[]) params
	);
  }

  @Override
  public <T> Select call(TryFunction<ValueReader, T, SQLException> mapper, Consumer<T> consumer) {
	if (null == mapper) throw new NullPointerException("Mapper must be provided");
	if (null == consumer) throw new NullPointerException("Consumer must be provided");
	this.finisher = () -> {
	  if (mapper != null && consumer != null && isPrepared) {
		try {
		  consumer.accept(mapper.apply(ValueGetters.reader(
				  new MetadataImpl(null, ((CallableStatement) statement)::getMetaData, metaCache).snapshot(),
				  (CallableStatement) statement
		  )));
		} catch (SQLException e) {
		  throw new RuntimeException(e);
		}
	  }
	};
	return this;
  }

  @Override
  protected MetadataImpl preloadMetadata(Statement statement) {
	// Stored procedures can be stateful and must never be executed a second time as a metadata probe.
	return null;
  }

  @Override
  public StoredProcedure timeout(int timeout) {
	return (StoredProcedure) super.timeout(timeout);
  }

  @Override
  public StoredProcedure skipWarnings(boolean skipWarnings) {
	return (StoredProcedure) super.skipWarnings(skipWarnings);
  }

  @Override
  public StoredProcedure print(Consumer<String> printer) {
	return (StoredProcedure) super.print(printer);
  }

  @Override
  @SuppressWarnings("unchecked")
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
	  ValueWriter writer = ValueSetters.writer(callableStatement);
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
