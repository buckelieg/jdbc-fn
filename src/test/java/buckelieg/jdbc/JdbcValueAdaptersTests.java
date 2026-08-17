package buckelieg.jdbc;

import org.junit.jupiter.api.Test;

import javax.sql.RowSet;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcValueAdaptersTests {

  @Test
  void resultSetReaderDelegatesEveryGetterWithoutChangingArgumentsOrResults() throws Exception {
	AtomicReference<Invocation> invocation = new AtomicReference<>();
	ResultSet resultSet = proxy(ResultSet.class, (proxy, method, args) -> {
	  Object result = result(method.getReturnType());
	  invocation.set(new Invocation(method, args, result));
	  return result;
	});
	Metadata metadata = proxy(Metadata.class, (proxy, method, args) -> result(method.getReturnType()));
	ValueReader reader = ValueGetters.reader(metadata, resultSet);

	assertSame(metadata, reader.meta());
	for (Method method : ValueReader.class.getDeclaredMethods()) {
	  if ("meta".equals(method.getName())) continue;
	  Object[] arguments = arguments(method.getParameterTypes());
	  Object returned = invoke(method, reader, arguments);
	  Invocation delegated = invocation.getAndSet(null);

	  assertEquals(method.getName(), delegated.method.getName(), method.toString());
	  assertArrayEquals(method.getParameterTypes(), delegated.method.getParameterTypes(), method.toString());
	  assertArrayEquals(arguments, delegated.arguments, method.toString());
	  if (method.getReturnType().isPrimitive()) assertEquals(delegated.result, returned, method.toString());
	  else assertSame(delegated.result, returned, method.toString());
	}
  }

  @Test
  @SuppressWarnings("deprecation")
  void callableReaderSupportsNamedIndexedTypedAndLegacyValues() throws Exception {
	List<String> calls = new ArrayList<>();
	ResultSetMetaData resultSetMetaData = metadata("ID", "AMOUNT");
	Blob blob = proxy(Blob.class, (proxy, method, args) -> {
	  if ("getBinaryStream".equals(method.getName())) return new ByteArrayInputStream(new byte[]{7, 8});
	  return result(method.getReturnType());
	});
	CallableStatement statement = proxy(CallableStatement.class, (proxy, method, args) -> {
	  calls.add(method.getName() + Arrays.toString(null == args ? new Object[0] : args));
	  switch (method.getName()) {
		case "getInt": return 17;
		case "getString": return "value";
		case "getObject": return "typed";
		case "getBytes": return new byte[]{1, 2, 3};
		case "getBlob": return blob;
		case "getMetaData": return resultSetMetaData;
		case "getBigDecimal": return BigDecimal.TEN;
		default: return result(method.getReturnType());
	  }
	});
	ValueReader reader = ValueGetters.reader(proxy(Metadata.class, (proxy, method, args) -> null), statement);

	assertEquals(17, reader.getInt(2));
	assertEquals("value", reader.getString("name"));
	assertEquals("typed", reader.getObject(2, String.class));
	assertArrayEquals(new byte[]{1, 2, 3}, read(reader.getAsciiStream("payload")));
	assertArrayEquals(new byte[]{7, 8}, read(reader.getUnicodeStream(3)));
	assertEquals(BigDecimal.TEN, reader.getBigDecimal("AMOUNT", 2));
	assertEquals("getBigDecimal[2, 2]", calls.get(calls.size() - 1));
  }

  @Test
  void preparedStatementWriterDelegatesCompleteWriterSurfaceAndResolvesNames() throws Exception {
	List<Invocation> calls = new ArrayList<>();
	PreparedStatement statement = proxy(PreparedStatement.class, (proxy, method, args) -> {
	  if ("getMetaData".equals(method.getName())) return metadata("VALUE");
	  calls.add(new Invocation(method, args, null));
	  return result(method.getReturnType());
	});
	ValueWriter writer = ValueSetters.writer(statement);

	for (Method method : ValueWriter.class.getDeclaredMethods()) {
	  Object[] arguments = arguments(method.getParameterTypes());
	  invoke(method, writer, arguments);
	  Invocation delegated = calls.get(calls.size() - 1);

	  assertEquals(method.getName(), delegated.method.getName(), method.toString());
	  assertEquals(Integer.class, delegated.arguments[0].getClass(), method.toString());
	  assertEquals(String.class == method.getParameterTypes()[0] ? 1 : arguments[0], delegated.arguments[0], method.toString());
	}
  }

  @Test
  void writerRoutesToCallableResultSetAndRowSetSpecificApis() throws Exception {
	List<String> callableCalls = new ArrayList<>();
	CallableStatement callable = proxy(CallableStatement.class, (proxy, method, args) -> {
	  callableCalls.add(method.getName() + Arrays.toString(args));
	  return result(method.getReturnType());
	});
	ValueWriter callableWriter = ValueSetters.writer(callable);
	callableWriter.setInt(2, 9);
	callableWriter.setString("name", "value");
	assertEquals(Arrays.asList("setInt[2, 9]", "setString[name, value]"), callableCalls);

	List<String> resultSetCalls = new ArrayList<>();
	ResultSet resultSet = proxy(ResultSet.class, (proxy, method, args) -> {
	  if ("getMetaData".equals(method.getName())) return metadata("VALUE");
	  resultSetCalls.add(method.getName());
	  return result(method.getReturnType());
	});
	ValueWriter resultSetWriter = ValueSetters.writer(resultSet);
	resultSetWriter.setInt(1, 9);
	resultSetWriter.setString("VALUE", "value");
	resultSetWriter.setNull(1, JDBCType.INTEGER.getVendorTypeNumber());
	assertEquals(Arrays.asList("updateInt", "updateString", "updateNull"), resultSetCalls);

	List<String> rowSetCalls = new ArrayList<>();
	RowSet rowSet = proxy(RowSet.class, (proxy, method, args) -> {
	  if ("getMetaData".equals(method.getName())) return metadata("VALUE");
	  rowSetCalls.add(method.getName());
	  return result(method.getReturnType());
	});
	ValueWriter rowSetWriter = ValueSetters.writer(rowSet);
	rowSetWriter.setInt(1, 9);
	rowSetWriter.setString("VALUE", "value");
	rowSetWriter.setNull(1, JDBCType.INTEGER.getVendorTypeNumber());
	assertEquals(Arrays.asList("setInt", "setString", "setNull"), rowSetCalls);
  }

  @Test
  void factoriesRejectNullJdbcObjects() {
	Metadata metadata = proxy(Metadata.class, (proxy, method, args) -> null);
	assertThrows(NullPointerException.class, () -> ValueGetters.reader(metadata, (ResultSet) null));
	assertThrows(NullPointerException.class, () -> ValueGetters.reader(metadata, (CallableStatement) null));
	assertThrows(NullPointerException.class, () -> ValueSetters.writer((ResultSet) null));
	assertThrows(NullPointerException.class, () -> ValueSetters.writer((PreparedStatement) null));
  }

  private static ResultSetMetaData metadata(String... names) {
	return proxy(ResultSetMetaData.class, (proxy, method, args) -> {
	  if ("getColumnCount".equals(method.getName())) return names.length;
	  if ("getColumnName".equals(method.getName())) return names[(Integer) args[0] - 1];
	  return result(method.getReturnType());
	});
  }

  private static byte[] read(InputStream stream) throws Exception {
	byte[] bytes = new byte[16];
	int count = stream.read(bytes);
	return Arrays.copyOf(bytes, count);
  }

  private static Object invoke(Method method, Object target, Object[] arguments) throws Exception {
	try {
	  return method.invoke(target, arguments);
	} catch (InvocationTargetException e) {
	  Throwable cause = e.getCause();
	  if (cause instanceof Exception) throw (Exception) cause;
	  throw e;
	}
  }

  private static Object[] arguments(Class<?>[] types) throws Exception {
	Object[] arguments = new Object[types.length];
	for (int i = 0; i < types.length; i++) arguments[i] = argument(types[i]);
	return arguments;
  }

  private static Object argument(Class<?> type) throws Exception {
	if (String.class == type) return "VALUE";
	if (boolean.class == type) return true;
	if (byte.class == type) return (byte) 2;
	if (short.class == type) return (short) 3;
	if (int.class == type) return 2;
	if (long.class == type) return 4L;
	if (float.class == type) return 1.5F;
	if (double.class == type) return 2.5D;
	if (byte[].class == type) return new byte[]{1, 2};
	if (BigDecimal.class == type) return BigDecimal.ONE;
	if (InputStream.class == type) return new ByteArrayInputStream(new byte[]{1});
	if (Reader.class == type) return new StringReader("value");
	if (Date.class == type) return new Date(1L);
	if (Time.class == type) return new Time(1L);
	if (Timestamp.class == type) return new Timestamp(1L);
	if (Calendar.class == type) return Calendar.getInstance();
	if (URL.class == type) return new URL("https://example.test");
	if (Class.class == type) return String.class;
	if (Map.class == type) return Collections.singletonMap("type", String.class);
	if (SQLType.class == type) return JDBCType.VARCHAR;
	if (Object.class == type) return "object";
	if (type.isInterface()) return proxy(type, (proxy, method, args) -> result(method.getReturnType()));
	return null;
  }

  private static Object result(Class<?> type) {
	if (Void.TYPE == type) return null;
	if (boolean.class == type) return true;
	if (byte.class == type) return (byte) 2;
	if (short.class == type) return (short) 3;
	if (int.class == type) return 17;
	if (long.class == type) return 19L;
	if (float.class == type) return 1.5F;
	if (double.class == type) return 2.5D;
	if (String.class == type) return "result";
	if (byte[].class == type) return new byte[]{1, 2};
	if (BigDecimal.class == type) return BigDecimal.TEN;
	if (InputStream.class == type) return new ByteArrayInputStream(new byte[]{1, 2});
	if (Reader.class == type) return new StringReader("result");
	if (Date.class == type) return new Date(1L);
	if (Time.class == type) return new Time(1L);
	if (Timestamp.class == type) return new Timestamp(1L);
	if (URL.class == type) {
	  try { return new URL("https://example.test"); } catch (Exception e) { throw new AssertionError(e); }
	}
	if (Map.class == type) return Collections.singletonMap("type", String.class);
	if (Object.class == type) return "object";
	if (type.isInterface()) return proxy(type, (proxy, method, args) -> result(method.getReturnType()));
	return null;
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
	return (T) Proxy.newProxyInstance(JdbcValueAdaptersTests.class.getClassLoader(), new Class<?>[]{type}, handler);
  }

  private static final class Invocation {
	final Method method;
	final Object[] arguments;
	final Object result;

	Invocation(Method method, Object[] arguments, Object result) {
	  this.method = method;
	  this.arguments = null == arguments ? new Object[0] : arguments;
	  this.result = result;
	}
  }

}
