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

import buckelieg.jdbc.fn.TryConsumer;
import buckelieg.jdbc.fn.TrySupplier;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static buckelieg.jdbc.Utils.*;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.of;

/**
 * A database query factory
 *
 * @see Select
 * @see Update
 * @see StoredProcedure
 * @see Script
 */
@ParametersAreNonnullByDefault
public class Session {

  protected final TrySupplier<Connection, SQLException> connectionSupplier;
  final Map<String, RSMeta.Column> metaCache;
  protected final TryConsumer<Connection, ? extends Throwable> connectionCloser;
  protected final Supplier<ExecutorService> executorServiceSupplier;

  Session(
		  Map<String, RSMeta.Column> metaCache,
		  TrySupplier<Connection, SQLException> connectionSupplier,
		  TryConsumer<Connection, ? super Throwable> connectionCloser,
		  Supplier<ExecutorService> executorServiceSupplier) {
	this.connectionSupplier = connectionSupplier;
	this.metaCache = metaCache;
	this.connectionCloser = connectionCloser;
	this.executorServiceSupplier = executorServiceSupplier;
  }

  /**
   * Executes a series of an arbitrary SQL statement(s)
   *
   * @param script          (a series of) SQL statement(s) to execute
   * @param namedParameters named parameters to be used in the script
   * @return script query
   * @throws NullPointerException if <code>script</code> is null
   * @see Script
   */
  @Nonnull
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Script script(String script, Map<String, ?> namedParameters) {
	return new ScriptQuery(connectionSupplier, connectionCloser, executorServiceSupplier, wipeComments(requireNonNull(script, "SQL script must be provided")), namedParameters.entrySet());
  }

  /**
   * Executes a series of an arbitrary SQL statement(s)
   *
   * @param script          (a series of) SQL statement(s) to execute
   * @param namedParameters named parameters to be used in the script
   * @return script query
   * @throws NullPointerException if <code>script</code> is null
   * @see Script
   */
  @SafeVarargs
  @Nonnull
  @SuppressWarnings({"unchecked", "rawtypes"})
  public final <T extends Map.Entry<String, ?>> Script script(String script, T... namedParameters) {
	return new ScriptQuery(connectionSupplier, connectionCloser, executorServiceSupplier, wipeComments(requireNonNull(script, "SQL script must be provided")), asList(namedParameters));
  }

  /**
   * Executes a series of an arbitrary SQL statement(s) from provided source file
   *
   * @param source          file with a SQL script contained
   * @param encoding        source file encoding to be used
   * @param namedParameters named parameters to be used in the script
   * @return script query
   * @throws NullPointerException if either <code>source</code> file or file <code>encoding</code> is null
   * @throws RuntimeException     if provided file is directory or does not exist or other IO errors
   * @see #script(String, Map.Entry[])
   * @see Charset
   */
  @Nonnull
  public <T extends Map.Entry<String, ?>> Script script(File source, Charset encoding, T... namedParameters) {
	try {
	  return script(new String(readAllBytes(requireNonNull(source, "Source file must be provided").toPath()), requireNonNull(encoding, "File encoding must be provided")), namedParameters);
	} catch (Exception e) {
	  throw new RuntimeException(e);
	}
  }

  /**
   * Executes a series of an arbitrary SQL statement(s) with default encoding (<code>Charset.UTF_8</code>)
   *
   * @param source          file with a SQL script contained to execute
   * @param namedParameters named parameters to be used in the script
   * @return script query
   * @throws RuntimeException in case of any errors (like {@link java.io.FileNotFoundException} or source file is null)
   * @see #script(File, Charset, Map.Entry[])
   */
  @Nonnull
  public <T extends Map.Entry<String, ?>> Script script(File source, T... namedParameters) {
	return script(source, UTF_8, namedParameters);
  }

  /**
   * Calls stored procedure
   *
   * @param query procedure call string to execute
   * @return stored procedure call
   * @see StoredProcedure
   * @see #procedure(String, P[])
   */
  @Nonnull
  public StoredProcedure procedure(String query) {
	return procedure(query, new Object[0]);
  }

  /**
   * Calls stored procedure
   * <br/>Supplied parameters are considered as IN parameters
   *
   * @param query      procedure call string to execute
   * @param parameters procedure IN parameters' values
   * @return stored procedure call
   * @see StoredProcedure
   * @see #procedure(String, P[])
   */
  @Nonnull
  public StoredProcedure procedure(String query, Object... parameters) {
	return procedure(query, stream(parameters).map(P::in).collect(toList()).toArray(new P<?>[parameters.length]));
  }

  /**
   * Creates a stored procedure call
   * <br/>Parameter names are CASE SENSITIVE!
   * <br/>So that :NAME and :name are two different parameters
   * <br/>Named parameters order must match parameters type of the procedure called
   *
   * @param query      procedure call string to execute
   * @param parameters procedure parameters as declared (IN/OUT/INOUT)
   * @return stored procedure call
   * @throws IllegalArgumentException if provided query is not valid DML statement or named parameters provided along with unnamed ones
   * @see StoredProcedure
   */
  @Nonnull
  public StoredProcedure procedure(String query, P<?>... parameters) {
	query = checkSingle(requireNonNull(query, "SQL query must be provided"));
	if (isAnonymous(query) && !isProcedure(query)) {
	  throw new IllegalArgumentException(format("Query '%s' is not valid procedure call statement", query));
	} else {
	  int namedParams = (int) of(parameters).filter(p -> !p.getName().isEmpty()).count();
	  if (namedParams == parameters.length && parameters.length > 0) {
		Map.Entry<String, Object[]> preparedQuery = prepareQuery(query, of(parameters).map(p -> entry(p.getName(), new P<?>[]{p})).collect(toList()));
		query = preparedQuery.getKey();
		parameters = stream(preparedQuery.getValue()).map(p -> (P<?>) p).toArray(P[]::new);
	  } else if (0 < namedParams && namedParams < parameters.length) {
		throw new IllegalArgumentException(format(
				"Cannot combine named parameters (count = %s) with unnamed ones (count = %s)",
				namedParams, parameters.length - namedParams
		));
	  }
	}
	return new StoredProcedureQuery(metaCache, connectionSupplier, connectionCloser, executorServiceSupplier, query, parameters);
  }

  /**
   * Executes SELECT statement
   *
   * @param query      SELECT query to execute
   * @param parameters query parameters in the declared order of '?'
   * @return select query
   * @throws IllegalArgumentException if provided query is a procedure call statement
   * @see Select
   */
  @Nonnull
  public Select select(String query, Object... parameters) {
	requireNonNull(query, "SQL query must be provided");
	if (isProcedure(query)) throw new IllegalArgumentException(format("Query '%s' is not valid select statement", query));
	return new SelectQuery(metaCache, connectionSupplier, connectionCloser, executorServiceSupplier, checkAnonymous(checkSingle(query)), parameters);
  }

  /**
   * Executes SELECT statement
   *
   * @param query SELECT query to execute
   * @return select query
   * @throws IllegalArgumentException if provided query is a procedure call statement
   * @see Select
   */
  @Nonnull
  public Select select(String query) {
	return select(query, new Object[0]);
  }

  /**
   * Executes SELECT statement
   * <br/>Parameter names are CASE SENSITIVE!
   * <br/>So that :NAME and :name are two different parameters
   *
   * @param query           SELECT query to execute
   * @param namedParameters query named parameters in the form of :name
   * @return select query
   * @throws IllegalArgumentException if provided query is a procedure call statement
   * @see Select
   */
  @Nonnull
  public Select select(String query, Map<String, ?> namedParameters) {
	return select(query, requireNonNull(namedParameters, "Named parameters must be provided").entrySet());
  }

  /**
   * Executes SELECT statement with named parameters
   * <br/>Parameter names are CASE SENSITIVE!
   * <br/>So that :NAME and :name are two different parameters
   *
   * @param query           SELECT query to execute
   * @param namedParameters query named parameters in the form of :name
   * @return select query
   * @throws IllegalArgumentException if provided query is a procedure call statement
   * @see Select
   */
  @Nonnull
  @SafeVarargs
  public final <T extends Map.Entry<String, ?>> Select select(String query, T... namedParameters) {
	return select(query, asList(namedParameters));
  }

  /**
   * Executes statements: INSERT, UPDATE or DELETE
   * <br/>Parameter names are CASE SENSITIVE!
   * <br/>So that :NAME and :name are two different parameters
   *
   * @param query INSERT/UPDATE/DELETE query to execute
   * @param batch an array of query named parameters in the form of :name
   * @return update query
   * @throws IllegalArgumentException if provided query is a procedure call statement
   * @see Update
   */
  @Nonnull
  public Update update(String query, Map<String, ?>... batch) {
	List<Map.Entry<String, Object[]>> params = of(batch).map(np -> prepareQuery(query, np.entrySet())).collect(toList());
	return update(params.get(0).getKey(), params.stream().map(Map.Entry::getValue).collect(toList()).toArray(new Object[params.size()][]));
  }

  /**
   * Executes DML statements: INSERT, UPDATE or DELETE
   *
   * @param query INSERT/UPDATE/DELETE query to execute
   * @param batch an array of query parameters on the declared order of '?'
   * @return update query
   * @throws IllegalArgumentException if provided query is a procedure call statement
   * @see Update
   */
  @Nonnull
  public Update update(String query, Object[]... batch) {
	requireNonNull(query, "SQL query must be provided");
	if (isProcedure(query)) throw new IllegalArgumentException(format("Query '%s' is not valid DML statement", query));
	return new UpdateQuery(connectionSupplier, connectionCloser, executorServiceSupplier, checkAnonymous(checkSingle(query)), batch);
  }

  /**
   * Executes statements: INSERT, UPDATE or DELETE
   *
   * @param query INSERT/UPDATE/DELETE query to execute
   * @return update query
   * @throws IllegalArgumentException if provided query is a procedure call statement
   * @see Update
   */
  @Nonnull
  public Update update(String query) {
	return update(query, new Object[0]);
  }

  /**
   * Executes statements: INSERT, UPDATE or DELETE
   *
   * @param query      INSERT/UPDATE/DELETE query to execute
   * @param parameters query parameters on the declared order of '?'
   * @return update query
   * @throws IllegalArgumentException if provided query is a procedure call statement
   * @see Update
   */
  @Nonnull
  public Update update(String query, Object... parameters) {
	return update(query, new Object[][]{parameters});
  }

  /**
   * Executes statements: INSERT, UPDATE or DELETE
   * <br/>Parameter names are CASE SENSITIVE!
   * <br/>So that :NAME and :name are two different parameters
   *
   * @param query           INSERT/UPDATE/DELETE query to execute
   * @param namedParameters query named parameters in the form of :name
   * @return update query
   * @throws IllegalArgumentException if provided query is a procedure call statement
   * @see Update
   */
  @Nonnull
  public <T extends Map.Entry<String, ?>> Update update(String query, T... namedParameters) {
	return update(query, asList(namedParameters));
  }

  private Select select(String query, Iterable<? extends Map.Entry<String, ?>> namedParams) {
	return prepare(query, namedParams, this::select);
  }

  private Update update(String query, Iterable<? extends Map.Entry<String, ?>> namedParams) {
	return prepare(query, namedParams, this::update);
  }

  private <T extends Query<T>> T prepare(String query, Iterable<? extends Map.Entry<String, ?>> namedParams, BiFunction<String, Object[], T> toQuery) {
	Map.Entry<String, Object[]> preparedQuery = prepareQuery(wipeComments(query), namedParams);
	return toQuery.apply(preparedQuery.getKey(), preparedQuery.getValue());
  }

}
