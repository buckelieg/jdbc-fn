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

import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * An abstraction for arbitrary SQL query
 *
 * @see Select
 * @see Update
 * @see StoredProcedure
 * @see Script
 */
public interface Query<Q extends Query<Q>> {

  /**
   * Executes this arbitrary SQL query
   *
   * @return an arbitrary query execution result(s)
   * @see Select#execute()
   * @see Update#execute()
   * @see StoredProcedure#execute()
   * @see Script#execute()
   */
  <T> T execute();

  /**
   * Tells JDBC driver if this query is poolable
   *
   * @param poolable true if this query is poolable, false otherwise
   * @return a query abstraction
   * @see java.sql.Statement#setPoolable(boolean)
   */
  Q poolable(boolean poolable);

  /**
   * Sets query execution timeout (in seconds)
   * <br/>Negative values are silently ignored
   *
   * @param timeout query timeout in seconds greater than 0 (0 means no timeout)
   * @return a query abstraction
   * @see java.sql.Statement#setQueryTimeout(int)
   */
  default Q timeout(int timeout) {
	return timeout(timeout, TimeUnit.SECONDS);
  }

  /**
   * Sets query execution timeout
   * <br/>Negative values are silently ignored
   * <br/>TimeUnit is converted to seconds
   *
   * @param timeout query timeout in seconds greater than 0 (0 means no timeout)
   * @param unit    a time unit
   * @return a query abstraction
   * @throws NullPointerException if <code>unit</code> is null
   * @see #timeout(int)
   * @see TimeUnit
   */
  Q timeout(int timeout, TimeUnit unit);

  /**
   * Sets query execution timeout
   *
   * @param duration a duration of timeout (will be converted to seconds)
   * @return a query abstraction
   */
  default Q timeout(Duration duration) {
	if (null == duration) throw new NullPointerException("Duration must be provided");
	return timeout((int) duration.getSeconds());
  }

  /**
   * Sets escape processing for this query
   *
   * @param escapeProcessing true (the default) if escape processing is enabled, false - otherwise
   * @return a query abstraction
   * @see java.sql.Statement#setEscapeProcessing(boolean)
   */
  Q escaped(boolean escapeProcessing);


  /**
   * Sets flag whether to skip on warnings or not
   *
   * @param skipWarnings true (the default) if to skip warnings, false - otherwise
   * @return a query abstraction
   * @implNote default value is <code>true</code>
   */
  Q skipWarnings(boolean skipWarnings);

  /**
   * Prints this query string (as SQL) to provided logger
   * <br/>All parameters are substituted by calling theirs' <code>toString()</code> methods
   *
   * @param printer query string consumer
   * @return a query abstraction
   * @throws NullPointerException if <code>printer</code> is null
   */
  Q print(Consumer<String> printer);

  /**
   * Prints this query string (as SQL) to standard output
   * <br/>All parameters are substituted by calling theirs' <code>toString()</code> methods
   *
   * @return a query abstraction
   * @see System#out
   * @see PrintStream#println
   */
  default Q print() {
	return print(System.out::println);
  }

  /**
   * Represents this <code>query</code> AS <code>SQL</code> string
   * <br/>All parameters are substituted by calling theirs' <code>toString()</code> methods
   *
   * @return this query as a SQL string
   */
  String asSQL();

}
