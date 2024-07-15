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

import buckelieg.jdbc.fn.TryFunction;
import buckelieg.jdbc.fn.TryQuadFunction;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.BaseStream;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Stream.of;
import static java.util.stream.StreamSupport.stream;

enum Utils {
  ;
  static final String EXCEPTION_MESSAGE = "Unsupported operation";
  static final String STATEMENT_DELIMITER = ";";
  static final Pattern PARAMETER = compile("\\?");
  private static final String QUOTATION_ESCAPE = "(?=(([^\"']*\"'){2})*[^\"']*$)";
  static final Pattern NAMED_PARAMETER = compile(format("(:\\w*\\b)%s", QUOTATION_ESCAPE));
  private static final Pattern STATEMENT_DELIMITER_PATTERN = compile(format("%s%s+", STATEMENT_DELIMITER, QUOTATION_ESCAPE));

  // Java regexp does not support conditional regexps. We will enumerate all possible cases
  static final Pattern STORED_PROCEDURE = compile(
		  format(
				  "%s|%s|%s|%s|%s|%s",
				  "(\\?\\s*=\\s*)?call\\s+(\\w+.{1}){0,2}\\w+\\s*(\\(\\s*)\\)",
				  "(\\?\\s*=\\s*)?call\\s+(\\w+.{1}){0,2}\\w+\\s*((\\(\\s*)\\?\\s*)(,\\s*\\?)*\\)",
				  "(\\?\\s*=\\s*)?call\\s+(\\w+.{1}){0,2}\\w+",
				  "\\{\\s*(\\?\\s*=\\s*)?call\\s+(\\w+.{1}){0,2}\\w+\\s*\\}",
				  "\\{\\s*(\\?\\s*=\\s*)?call\\s+(\\w+.{1}){0,2}\\w+\\s*((\\(\\s*)\\?\\s*)(,\\s*\\?)*\\)\\s*\\}",
				  "\\{\\s*(\\?\\s*=\\s*)?call\\s+(\\w+.{1}){0,2}\\w+\\s*(\\(\\s*)\\)\\s*\\}"
		  ),
		  Pattern.CASE_INSENSITIVE
  );

  @Nonnull
  static Entry<String, Object[]> prepareQuery(String query, Iterable<? extends Entry<String, ?>> namedParams) {
	Map<Integer, Object> indicesToValues = new TreeMap<>();
	Map<String, Optional<?>> transformedParams = stream(namedParams.spliterator(), false).collect(toMap(
			e -> e.getKey().startsWith(":") ? e.getKey() : format(":%s", e.getKey()),
			e -> ofNullable(e.getValue()) // HashMap/ConcurrentHashMap merge function fails on null values
	));
	Matcher matcher = NAMED_PARAMETER.matcher(query);
	int idx = 0;
	while (matcher.find())
	  for (Object o : asIterable(transformedParams.getOrDefault(matcher.group(), empty())))
		indicesToValues.put(++idx, o);
	for (Entry<String, Optional<?>> e : transformedParams.entrySet()) {
	  query = query.replaceAll(
			  format("(%s\\b)%s", e.getKey(), QUOTATION_ESCAPE),
			  stream(asIterable(e.getValue()).spliterator(), false).map(o -> "?").collect(joining(","))
	  );
	}
	return entry(checkAnonymous(query), indicesToValues.values().toArray());
  }

  @SuppressWarnings({"rawtypes", "unchecked", "OptionalUsedAsFieldOrParameterType"})
  @Nonnull
  private static Iterable<?> asIterable(Optional o) {
	Iterable<?> iterable;
	Object value = o.orElse(singletonList(null));
	if (value.getClass().isArray()) {
	  if (value instanceof Object[]) iterable = asList((Object[]) value);
	  else iterable = new BoxedPrimitiveIterable(value);
	} else if (value instanceof Iterable) iterable = (Iterable<?>) value;
	else iterable = singletonList(value);
	return iterable;
  }

  static boolean isProcedure(String query) {
	return STORED_PROCEDURE.matcher(query).matches();
  }

  static String checkAnonymous(String query) {
	if (!isAnonymous(query))
	  throw new IllegalArgumentException(format("Named parameters mismatch for query: '%s'", query));
	return query;
  }

  static boolean isAnonymous(String query) {
	return !NAMED_PARAMETER.matcher(query).find();
  }

  static SQLRuntimeException newSQLRuntimeException(Throwable... throwables) {
	StringBuilder messages = new StringBuilder();
	for (Throwable throwable : throwables) {
	  Optional<Throwable> t = ofNullable(throwable);
	  StringBuilder message = t
			  .map(Throwable::getMessage)
			  .map(String::trim)
			  .map(StringBuilder::new)
			  .map(msg -> msg.append(" "))
			  .orElse(new StringBuilder());
	  AtomicReference<String> prevMsg = new AtomicReference<>();
	  while ((t = t.map(Throwable::getCause)).orElse(null) != null) {
		t.map(Throwable::getMessage)
				.map(msg -> format("%s ", msg.trim()))
				.filter(msg -> prevMsg.get() != null && prevMsg.get().equals(msg))
				.ifPresentOrElse(
						msg -> {
						  message.append(msg);
						  prevMsg.set(msg);
						},
						() -> prevMsg.set(null)
				);
	  }
	  messages.append(message);
	}
	return new SQLRuntimeException(messages.toString().trim(), true);
  }

  // TODO retain SQL hint comments: /*+ */
  static String wipeComments(String query) {
	int queryIndex = -4;
	String replaced = query.replaceAll("\\R", "\r\n");
	List<Integer> singleLineCommentStartIndices = new ArrayList<>();
	List<Integer> singleLineCommentEndIndices = new ArrayList<>();
	List<Integer> multiLineCommentStartIndices = new ArrayList<>();
	List<Integer> multiLineCommentsEndIndices = new ArrayList<>();
	boolean isInsideComment = false;
	boolean isInsideQuotes = false;
	boolean isSingleLineComment = false;
	boolean isInnerComment = false;
	Character outerQuote = null;
	for (String line : replaced.split("\r\n")) {
	  queryIndex = queryIndex + 3;
	  if (line.isEmpty()) {
		queryIndex--;
		continue;
	  }
	  for (int i = 1; i < line.length(); i++) {
		++queryIndex;
		char prev = line.charAt(i - 1);
		char cur = line.charAt(i);
		if (isInsideQuotes) {
		  if ('\'' == prev || '"' == prev) {
			if (outerQuote != null && outerQuote.equals(prev)) {
			  isInsideQuotes = false;
			  outerQuote = null;
			}
		  }
		  continue;
		}
		if (isInsideComment) {
		  if (isSingleLineComment) continue;
		  if (!isInnerComment && ('*' == cur && '/' == prev)) {
			isInnerComment = true;
			continue;
		  }
		  if ('*' == prev && '/' == cur) {
			multiLineCommentsEndIndices.add(queryIndex + 2);
			isInnerComment = false;
			isInsideComment = false;
			continue;
		  }
		} else {
		  if ('-' == cur && '-' == prev) {
			singleLineCommentStartIndices.add(queryIndex);
			isInsideComment = true;
			isSingleLineComment = true;
			continue;
		  }
		  if ('*' == cur && '/' == prev) {
			isInsideComment = true;
			multiLineCommentStartIndices.add(queryIndex);
			continue;
		  }
		  if ('*' == prev && '/' == cur) {
			multiLineCommentsEndIndices.add(queryIndex + 2);
			isInsideComment = false;
			continue;
		  }
		}
		if ('\'' == prev || '"' == prev) {
		  isInsideQuotes = true;
		  outerQuote = prev;
		}
	  }
	  if (isInsideComment && isSingleLineComment) {
		singleLineCommentEndIndices.add(queryIndex + 2);
		isSingleLineComment = false;
		isInsideComment = false;
	  }
	}
	if (multiLineCommentStartIndices.size() != multiLineCommentsEndIndices.size()) {
	  throw new SQLRuntimeException(
			  format(
					  "Multiline comments open/close tags count mismatch (%s/%s) for query:\r\n%s",
					  multiLineCommentStartIndices.size(),
					  multiLineCommentsEndIndices.size(),
					  query
			  ),
			  true
	  );
	}
	if (!multiLineCommentStartIndices.isEmpty() && (multiLineCommentStartIndices.get(0) > multiLineCommentsEndIndices.get(0))) {
	  throw new SQLRuntimeException(
			  format(
					  "Unmatched start multiline comment at %s for query:\r\n%s",
					  multiLineCommentStartIndices.get(0),
					  query
			  ),
			  true
	  );
	}
	replaced = replaceChars(replaced, singleLineCommentStartIndices, singleLineCommentEndIndices);
	replaced = replaceChars(replaced, multiLineCommentStartIndices, multiLineCommentsEndIndices);
	return replaced.replaceAll("(\\s){2,}", " ").trim();
  }

  private static String replaceChars(String source, List<Integer> startIndices, List<Integer> endIndices) {
	String replaced = source;
	for (int i = 0; i < startIndices.size(); i++)
	  replaced = replaced.replace(
			  replaced.substring(startIndices.get(i), endIndices.get(i)),
			  format("%" + (endIndices.get(i) - startIndices.get(i)) + "s", " ")
	  );
	return replaced;
  }

  static String checkSingle(String query) {
	query = wipeComments(query);
	if (STATEMENT_DELIMITER_PATTERN.matcher(query).find())
	  throw new IllegalArgumentException(format("Query '%s' is not a single one", query));
	return query;
  }

  static <S extends PreparedStatement> S setStatementParameters(S statement, Object... params) throws SQLException {
	int pNum = 0;
	for (Object p : params) {
//            Mappers.setParameter(statement, ++pNum, p);
	  statement.setObject(++pNum, p);
	}
	return statement;
  }

  static String asSQL(String query, Object... params) {
	String replaced = query;
	int idx = 0;
	Matcher matcher = PARAMETER.matcher(query);
	while (matcher.find()) {
	  Object p = params[idx];
	  replaced = replaced.replaceFirst(
			  "\\?",
			  (p != null && p.getClass().isArray() ? of((Object[]) p) : of(ofNullable(p).orElse("null")))
					  .map(value -> value instanceof String ? format("'%s'", value.toString().replaceAll("\\$", "")) : value.toString())
					  .collect(joining(","))
	  );
	  idx++;
	}
	return replaced;
  }

  @Nonnull
  static <T> List<T> listResultSet(ResultSet resultSet, TryFunction<ResultSet, T, SQLException> mapper) throws SQLException {
	List<T> result = new ArrayList<>();
	while (resultSet.next())
	  result.add(requireNonNull(mapper.apply(resultSet)));
	return result;
  }

  @Nonnull
  static <K, V> Map.Entry<K, V> entry(K key, V value) {
	return new AbstractMap.SimpleImmutableEntry<>(key, value);
  }

  @SuppressWarnings("unchecked")
  @Nonnull
  static <T> T proxy(T instance, List<Class<?>> into, TryQuadFunction<T, Object, Method, Object[], Object, Exception> handler) {
	return (T) newProxyInstance(
			requireNonNull(instance, "Instance must be provided").getClass().getClassLoader(),
			requireNonNull(into, "Interface class must be provided").toArray(new Class[0]),
			(proxy, method, args) -> handler.apply(instance, proxy, method, args)
	);
  }

  static Object proxy(Object stream) {
	return proxy(stream, getAllInterfaces(stream.getClass()), (instance, proxy, method, args) -> {
	  if (BaseStream.class.equals(method.getDeclaringClass())) {
		if (!BaseStream.class.isAssignableFrom(method.getReturnType())) {
		  if ("iterator".equals(method.getName()) || "spliterator".equals(method.getName()))
			throw new UnsupportedOperationException(EXCEPTION_MESSAGE);
		  return method.invoke(instance, args);
		} else return proxy(method.invoke(instance, args));
	  }
	  if (BaseStream.class.isAssignableFrom(method.getDeclaringClass())) {
		if (!BaseStream.class.isAssignableFrom(method.getReturnType())) {
		  try (AutoCloseable proxied = (BaseStream<?, ?>) instance) {
			return method.invoke(proxied, args);
		  } catch (Throwable t) {
			throw newSQLRuntimeException(t.getCause(), t);
		  }
		} else return proxy(method.invoke(instance, args));
	  }
	  return method.invoke(instance, args);
	});
  }

  static List<Class<?>> getAllInterfaces(Class<?> cls) {
	Class<?> parent = cls;
	List<Class<?>> interfaces = new ArrayList<>();
	while (parent != null) {
	  interfaces.addAll(asList(parent.getInterfaces()));
	  parent = parent.getSuperclass();
	}
	return interfaces;
  }

  static PrimitiveIterator.OfInt newIntSequence() {
	AtomicInteger cursor = new AtomicInteger();
	return IntStream.generate(() -> cursor.getAndAdd(1)).iterator();
  }

}
