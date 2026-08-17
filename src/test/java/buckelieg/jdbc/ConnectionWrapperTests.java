package buckelieg.jdbc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConnectionWrapperTests {

  @Test
  void delegatesEveryConnectionOperationWithUnchangedArgumentsAndResult() throws Exception {
	AtomicReference<Invocation> invocation = new AtomicReference<>();
	Connection delegate = connection((proxy, method, args) -> {
	  Object result = result(method.getReturnType());
	  invocation.set(new Invocation(method, args, result));
	  return result;
	});
	Connection wrapper = new ConnectionWrapper(delegate);
	invocation.set(null); // Ignore state captured by the wrapper constructor.

	for (Method method : ConnectionWrapper.class.getDeclaredMethods()) {
	  if (!Modifier.isPublic(method.getModifiers()) || "close".equals(method.getName())) continue;
	  Object[] arguments = arguments(method.getParameterTypes());
	  Object returned = invoke(method, wrapper, arguments);
	  Invocation delegated = invocation.getAndSet(null);

	  assertEquals(method.getName(), delegated.method.getName(), method.toString());
	  assertArrayEquals(method.getParameterTypes(), delegated.method.getParameterTypes(), method.toString());
	  assertArrayEquals(arguments, delegated.arguments, method.toString());
	  if (Void.TYPE != method.getReturnType()) {
		if (method.getReturnType().isPrimitive()) assertEquals(delegated.result, returned, method.toString());
		else assertSame(delegated.result, returned, method.toString());
	  }
	}
  }

  @Test
  void closeRestoresConnectionStateBeforeClosingDelegate() throws Exception {
	List<String> calls = new ArrayList<>();
	Connection delegate = connection((proxy, method, args) -> {
	  switch (method.getName()) {
		case "getHoldability":
		  return ResultSet.HOLD_CURSORS_OVER_COMMIT;
		case "getAutoCommit":
		  return true;
		case "isReadOnly":
		  return false;
		case "getTransactionIsolation":
		  return Connection.TRANSACTION_SERIALIZABLE;
		default:
		  calls.add(method.getName() + Arrays.toString(null == args ? new Object[0] : args));
		  return result(method.getReturnType());
	  }
	});
	Connection wrapper = new ConnectionWrapper(delegate);

	wrapper.close();

	assertEquals(Arrays.asList(
			"setHoldability[1]",
			"setAutoCommit[true]",
			"setReadOnly[false]",
			"setTransactionIsolation[8]",
			"close[]"
	), calls);
  }

  private static Connection connection(java.lang.reflect.InvocationHandler handler) {
	return (Connection) Proxy.newProxyInstance(
			ConnectionWrapperTests.class.getClassLoader(),
			new Class<?>[]{Connection.class},
			handler
	);
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

  private static Object[] arguments(Class<?>[] types) {
	Object[] arguments = new Object[types.length];
	for (int i = 0; i < types.length; i++) arguments[i] = argument(types[i]);
	return arguments;
  }

  private static Object argument(Class<?> type) {
	if (String.class == type) return "value";
	if (boolean.class == type) return true;
	if (int.class == type) return 7;
	if (int[].class == type) return new int[]{1, 2};
	if (String[].class == type) return new String[]{"a", "b"};
	if (Object[].class == type) return new Object[]{"a", 2};
	if (Class.class == type) return Connection.class;
	if (Properties.class == type) return new Properties();
	if (java.util.Map.class == type) return Collections.singletonMap("type", String.class);
	if (Executor.class == type) return (Executor) Runnable::run;
	if (type.isInterface()) return Proxy.newProxyInstance(
			ConnectionWrapperTests.class.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> result(method.getReturnType())
	);
	return null;
  }

  private static Object result(Class<?> type) {
	if (Void.TYPE == type) return null;
	if (boolean.class == type) return true;
	if (byte.class == type) return (byte) 2;
	if (short.class == type) return (short) 3;
	if (int.class == type) return 7;
	if (long.class == type) return 11L;
	if (float.class == type) return 1.5F;
	if (double.class == type) return 2.5D;
	if (char.class == type) return 'x';
	if (String.class == type) return "result";
	if (SQLWarning.class == type) return new SQLWarning("warning");
	if (Properties.class == type) return new Properties();
	if (java.util.Map.class == type) return Collections.singletonMap("type", String.class);
	if (type.isInterface()) return Proxy.newProxyInstance(
			ConnectionWrapperTests.class.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> result(method.getReturnType())
	);
	return new Object();
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
