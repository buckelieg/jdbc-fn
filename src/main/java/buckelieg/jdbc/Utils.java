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

import buckelieg.jdbc.fn.*;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;
import static java.lang.reflect.Proxy.newProxyInstance;
import static java.sql.JDBCType.*;
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

final class Utils {

    static final String EXCEPTION_MESSAGE = "Unsupported operation";
    static final String STATEMENT_DELIMITER = ";";
    static final Pattern PARAMETER = compile("\\?");
    private static final String QUOTATION_ESCAPE = "(?=(([^\"']*\"'){2})*[^\"']*$)";
    static final Pattern NAMED_PARAMETER = compile(format("(:\\w*\\b)%s", QUOTATION_ESCAPE));
    private static final Pattern STATEMENT_DELIMITER_PATTERN = compile(format("%s%s+", STATEMENT_DELIMITER, QUOTATION_ESCAPE));

    // Java regexp does not support conditional regexps. We will enumerate all possible variants.
    static final Pattern STORED_PROCEDURE = compile(format("%s|%s|%s|%s|%s|%s",
            "(\\?\\s*=\\s*)?call\\s+(\\w+.{1}){0,2}\\w+\\s*(\\(\\s*)\\)",
            "(\\?\\s*=\\s*)?call\\s+(\\w+.{1}){0,2}\\w+\\s*((\\(\\s*)\\?\\s*)(,\\s*\\?)*\\)",
            "(\\?\\s*=\\s*)?call\\s+(\\w+.{1}){0,2}\\w+",
            "\\{\\s*(\\?\\s*=\\s*)?call\\s+(\\w+.{1}){0,2}\\w+\\s*\\}",
            "\\{\\s*(\\?\\s*=\\s*)?call\\s+(\\w+.{1}){0,2}\\w+\\s*((\\(\\s*)\\?\\s*)(,\\s*\\?)*\\)\\s*\\}",
            "\\{\\s*(\\?\\s*=\\s*)?call\\s+(\\w+.{1}){0,2}\\w+\\s*(\\(\\s*)\\)\\s*\\}"
    ), Pattern.CASE_INSENSITIVE);

    private static final Map<SQLType, TryBiFunction<ResultSet, Integer, Object, SQLException>> defaultReaders = new HashMap<>();
    private static final Map<SQLType, TryTriConsumer<ResultSet, Integer, Object, SQLException>> defaultWriters = new HashMap<>();

    static {
        // standard "recommended" readers
        defaultReaders.put(BINARY, ResultSet::getBytes);
        defaultReaders.put(VARBINARY, ResultSet::getBytes);
        defaultReaders.put(LONGVARBINARY, (input, index) -> {
            try (InputStream is = input.getBinaryStream(index); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) != -1) {
                    bos.write(buffer, 0, length);
                }
                return bos.toByteArray();
            } catch (Throwable t) {
                throw newSQLRuntimeException(t);
            }
        });
        defaultReaders.put(VARCHAR, ResultSet::getString);
        defaultReaders.put(CHAR, ResultSet::getString);
        defaultReaders.put(LONGVARCHAR, (input, index) -> {
            try (BufferedReader r = new BufferedReader(input.getCharacterStream(index))) {
                return r.lines().collect(joining("\r\n"));
            } catch (Throwable t) {
                throw newSQLRuntimeException(t);
            }
        });
        defaultReaders.put(DATE, ResultSet::getDate);
        defaultReaders.put(TIMESTAMP, ResultSet::getTimestamp);
        defaultReaders.put(TIMESTAMP_WITH_TIMEZONE, ResultSet::getTimestamp);
        defaultReaders.put(TIME, ResultSet::getTime);
        defaultReaders.put(TIME_WITH_TIMEZONE, ResultSet::getTime);
        defaultReaders.put(BIT, ResultSet::getBoolean);
        defaultReaders.put(TINYINT, ResultSet::getByte);
        defaultReaders.put(SMALLINT, ResultSet::getShort);
        defaultReaders.put(INTEGER, ResultSet::getInt);
        defaultReaders.put(BIGINT, ResultSet::getLong);
        defaultReaders.put(DECIMAL, ResultSet::getBigDecimal);
        defaultReaders.put(NUMERIC, ResultSet::getBigDecimal);
        defaultReaders.put(FLOAT, ResultSet::getDouble);
        defaultReaders.put(DOUBLE, ResultSet::getDouble);
        defaultReaders.put(REAL, ResultSet::getFloat);
        defaultReaders.put(JAVA_OBJECT, ResultSet::getObject);
        defaultReaders.put(OTHER, ResultSet::getObject);
    }

    static final TryFunction<ResultSet, Map<String, Object>, SQLException> defaultMapper = rs -> {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        Map<String, Object> result = new LinkedHashMap<>(columnCount);
        for (int col = 1; col <= columnCount; col++) {
            result.put(meta.getColumnLabel(col), defaultReaders.getOrDefault(valueOf(meta.getColumnType(col)), ResultSet::getObject).apply(rs, col));
        }
        return result;
    };

    static final class DefaultMapper implements TryFunction<ResultSet, Map<String, Object>, SQLException> {

        private TryFunction<ResultSet, Map<String, Object>, SQLException> mapper;
        private List<Entry<Entry<String, Integer>, TryBiFunction<ResultSet, Integer, Object, SQLException>>> colReaders;

        @Override
        public Map<String, Object> apply(ResultSet input) throws SQLException {
            if (mapper == null) {
                ResultSetMetaData meta = input.getMetaData();
                int columnCount = meta.getColumnCount();
                colReaders = new ArrayList<>(columnCount);
                for (int col = 1; col <= columnCount; col++) {
                    colReaders.add(new SimpleImmutableEntry<>(new SimpleImmutableEntry<>(meta.getColumnLabel(col), col), defaultReaders.getOrDefault(valueOf(meta.getColumnType(col)), ResultSet::getObject)));
                }
                mapper = rs -> {
                    Map<String, Object> result = new LinkedHashMap<>(columnCount);
                    for (Entry<Entry<String, Integer>, TryBiFunction<ResultSet, Integer, Object, SQLException>> e : colReaders) {
                        result.put(e.getKey().getKey(), e.getValue().apply(rs, e.getKey().getValue()));
                    }
                    return result;
                };
            }
            return mapper.apply(input);
        }
    }

    private Utils() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    static Entry<String, Object[]> prepareQuery(String query, Iterable<? extends Entry<String, ?>> namedParams) {
        Map<Integer, Object> indicesToValues = new TreeMap<>();
        Map<String, Optional<?>> transformedParams = stream(namedParams.spliterator(), false).collect(toMap(
                e -> e.getKey().startsWith(":") ? e.getKey() : format(":%s", e.getKey()),
                e -> ofNullable(e.getValue()) // HashMap/ConcurrentHashMap merge function fails on null values
        ));
        Matcher matcher = NAMED_PARAMETER.matcher(query);
        int idx = 0;
        while (matcher.find()) {
            for (Object o : asIterable(transformedParams.getOrDefault(matcher.group(), empty()))) {
                indicesToValues.put(++idx, o);
            }
        }
        for (Entry<String, Optional<?>> e : transformedParams.entrySet()) {
            query = query.replaceAll(
                    format("(%s\\b)%s", e.getKey(), QUOTATION_ESCAPE),
                    stream(asIterable(e.getValue()).spliterator(), false).map(o -> "?").collect(joining(","))
            );
        }
        return new SimpleImmutableEntry<>(checkAnonymous(query), indicesToValues.values().toArray());
    }

    @SuppressWarnings({"rawtypes", "unchecked", "OptionalUsedAsFieldOrParameterType"})
    private static Iterable<?> asIterable(Optional o) {
        Iterable<?> iterable;
        Object value = o.orElse(singletonList(null));
        if (value.getClass().isArray()) {
            if (value instanceof Object[]) {
                iterable = asList((Object[]) value);
            } else {
                iterable = new BoxedPrimitiveIterable(value);
            }
        } else if (value instanceof Iterable) {
            iterable = (Iterable<?>) value;
        } else {
            iterable = singletonList(value);
        }
        return iterable;
    }

    static boolean isProcedure(String query) {
        return STORED_PROCEDURE.matcher(query).matches();
    }

    static String checkAnonymous(String query) {
        if (!isAnonymous(query)) {
            throw new IllegalArgumentException(format("Named parameters mismatch for query: '%s'", query));
        }
        return query;
    }

    static boolean isAnonymous(String query) {
        return !NAMED_PARAMETER.matcher(query).find();
    }

    static SQLRuntimeException newSQLRuntimeException(Throwable... throwables) {
        StringBuilder messages = new StringBuilder();
        for (Throwable throwable : throwables) {
            Throwable t = throwable;
            StringBuilder message = ofNullable(t.getMessage()).map(msg -> new StringBuilder(format("%s ", msg.trim()))).orElse(new StringBuilder());
            AtomicReference<String> prevMsg = new AtomicReference<>();
            while ((t = t.getCause()) != null) {
                ofNullable(t.getMessage()).map(msg -> format("%s ", msg.trim())).filter(msg -> prevMsg.get() != null && prevMsg.get().equals(msg)).ifPresent(message::append);
                prevMsg.set(t.getMessage() != null ? t.getMessage().trim() : null);
            }
            messages.append(message);
        }
        return new SQLRuntimeException(messages.toString().trim(), true);
    }

    @SafeVarargs
    static <T extends Throwable> void collectAndThrow(TryRunnable<T>... actions) {
        if (actions != null && actions.length > 0) {
            List<Throwable> exceptions = new ArrayList<>();
            for (TryRunnable<T> action : actions) {
                try {
                    action.run();
                } catch (Throwable t) {
                    exceptions.add(t);
                }
            }
            if (!exceptions.isEmpty()) {
                throw newSQLRuntimeException(exceptions.toArray(new Throwable[0]));
            }
        }
    }

    static <T> T doInTransaction(
            TrySupplier<Connection, SQLException> connectionSupplier,
            TransactionIsolation isolationLevel,
            TryConsumer<Connection, SQLException> beforeStart,
            TryFunction<Connection, T, SQLException> action,
            TryConsumer<Connection, SQLException> onCommit
    ) throws SQLException {
        Connection conn = connectionSupplier.get();
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        Savepoint savepoint = null;
        int isolation = conn.getTransactionIsolation();
        T result;
        try {
            beforeStart.compose(connection -> {
                if (isolationLevel != null && isolation != isolationLevel.level) {
                    if (!conn.getMetaData().supportsTransactionIsolationLevel(isolationLevel.level)) {
                        throw new SQLException(format("Unsupported transaction isolation level: '%s'", isolationLevel.name()));
                    }
                    conn.setTransactionIsolation(isolationLevel.level);
                }
                autoCommit.set(conn.getAutoCommit());
                conn.setAutoCommit(false);
            }).accept(conn);
            result = action.apply(conn);
        } catch (SQLException e) {
            conn.rollback(savepoint);
            conn.releaseSavepoint(savepoint);
            throw e;
        } finally {
            onCommit.andThen(connection -> {
                conn.setAutoCommit(autoCommit.get());
                conn.setTransactionIsolation(isolation);
            }).accept(conn);
        }
        return result;
    }

    //    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    static <T> T doInTransaction(boolean forceClose, TrySupplier<Connection, SQLException> connectionSupplier, TransactionIsolation isolationLevel, TryFunction<Connection, T, SQLException> action) throws SQLException {
        Connection conn = connectionSupplier.get();
        boolean autoCommit = true;
        Savepoint savepoint = null;
        int isolation = conn.getTransactionIsolation();
        T result;
        try {
            autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            savepoint = conn.setSavepoint();
            if (isolationLevel != null && isolation != isolationLevel.level) {
                if (!conn.getMetaData().supportsTransactionIsolationLevel(isolationLevel.level)) {
                    throw new SQLException(format("Unsupported transaction isolation level: '%s'", isolationLevel.name()));
                }
                conn.setTransactionIsolation(isolationLevel.level);
            }
            result = action.apply(conn);
            conn.commit();
            return result;
        } catch (Exception e) {
            conn.rollback(savepoint);
            conn.releaseSavepoint(savepoint);
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
            conn.setTransactionIsolation(isolation);
            if (forceClose) {
                conn.close();
            }
        }
    }

    // TODO retain SQL hints comments: /*+ */
    static String cutComments(String query) {
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
                            "Multiline comments open/close tags count mismatch (%s/%s)  at index %s for query:\r\n%s",
                            multiLineCommentStartIndices.size(),
                            multiLineCommentsEndIndices.size(),
//                            startIndices.size() > endIndices.size() ? startIndices.get(endIndices.get(endIndices.size() - 1)) : endIndices.get(startIndices.get(startIndices.size() - 1)),
                            "", query
                    ),
                    true
            );
        }
        if (!multiLineCommentStartIndices.isEmpty() && (multiLineCommentStartIndices.get(0) > multiLineCommentsEndIndices.get(0))) {
            throw new SQLRuntimeException(format("Unmatched start multiline comment at %s for query:\r\n%s", multiLineCommentStartIndices.get(0), query), true);
        }
        for (int i = 0; i < singleLineCommentStartIndices.size(); i++) {
            replaced = replaced.replace(replaced.substring(singleLineCommentStartIndices.get(i), singleLineCommentEndIndices.get(i)), format("%" + (singleLineCommentEndIndices.get(i) - singleLineCommentStartIndices.get(i)) + "s", " "));
        }
        for (int i = 0; i < multiLineCommentStartIndices.size(); i++) {
            replaced = replaced.replace(replaced.substring(multiLineCommentStartIndices.get(i), multiLineCommentsEndIndices.get(i)), format("%" + (multiLineCommentsEndIndices.get(i) - multiLineCommentStartIndices.get(i)) + "s", " "));
        }
        return replaced.replaceAll("(\\s){2,}", " ").trim();
    }

    static String checkSingle(String query) {
        query = cutComments(query);
        if (STATEMENT_DELIMITER_PATTERN.matcher(query).find()) {
            throw new IllegalArgumentException(format("Query '%s' is not a single one", query));
        }
        return query;
    }

    static <S extends PreparedStatement> S setStatementParameters(S statement, Object... params) throws SQLException {
        int pNum = 0;
        for (Object p : params) {
            statement.setObject(++pNum, p); // introduce type conversion here?
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
                            .map(Object::toString)
                            .collect(joining(","))
            );
            idx++;
        }
        return replaced;
    }

    static <T> Stream<T> rsStream(ResultSet resultSet, TryFunction<ResultSet, T, SQLException> mapper) {
        return StreamSupport.stream(new ResultSetSpliterator(resultSet), false)
                .map(rs -> {
                    try {
                        return mapper.apply(rs);
                    } catch (SQLException e) {
                        try {
                            rs.close();
                        } catch (SQLException e1) {
                            throw newSQLRuntimeException(e1);
                        }
                        throw newSQLRuntimeException(e);
                    }
                })
                .onClose(() -> {
                    try {
                        resultSet.close();
                    } catch (SQLException e) {
                        throw newSQLRuntimeException(e);
                    }
                });
    }

    @Nonnull
    static <T> T wrap(T instance, Class<T> into) {
        return wrap(instance, into, (source, proxy, method, args) -> method.invoke(source, args));
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    static <T> T wrap(Object instance, Class<T> into, TryQuadFunction<T, Object, Method, Object[], Object, Throwable> handler) {
        return (T) newProxyInstance(requireNonNull(instance, "Instance must be provided").getClass().getClassLoader(), new Class<?>[]{into}, (proxy, method, args) -> handler.apply((T) instance, proxy, method, args));
    }

}
