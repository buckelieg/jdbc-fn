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

/**
 * Represents a {@link RuntimeException} wrapper for {@link java.sql.SQLException}
 *
 * @see java.sql.SQLException
 * @see RuntimeException
 */
public class SQLRuntimeException extends RuntimeException {

  private String message;

  /**
   * Constructs a new runtime exception with {@code null} as its detail message
   * <br/>The cause is not initialized, and may subsequently be initialized by a call to {@link #initCause}
   */
  public SQLRuntimeException() {
	super();
  }

  /**
   * Constructs a new runtime exception with the specified detail message
   * <br/>The cause is not initialized, and may subsequently be initialized by a call to {@link #initCause}
   *
   * @param message the detail message. The detail message is saved for
   *                later retrieval by the {@link #getMessage()} method
   */
  public SQLRuntimeException(String message) {
	super(message);
  }

  /**
   * Constructs a new runtime exception with provided string as its detail message
   * <br/>If <code>fillInStackTrace</code> parameter is set to <code>true</code> then stack trace is filled in, otherwise - not
   *
   * @param message          exception message string
   * @param fillInStackTrace whether to fill in stack trace or not
   */
  public SQLRuntimeException(String message, boolean fillInStackTrace) {
	this.message = message;
	if (fillInStackTrace) {
	  fillInStackTrace();
	}
  }

  /**
   * Constructs a new runtime exception with the specified detail message and cause
   * <p>Note that the detail message associated with {@code cause} is <i>not</i> automatically incorporated in this runtime exception's detail message
   *
   * @param message the detail message (which is saved for later retrieval
   *                by the {@link #getMessage()} method)
   * @param cause   the cause (which is saved for later retrieval by the
   *                {@link #getCause()} method).  (A {@code null} value is
   *                permitted, and indicates that the cause is nonexistent or
   *                unknown)
   */
  public SQLRuntimeException(String message, Throwable cause) {
	super(message, cause);
  }

  /**
   * Constructs a new runtime exception with the specified cause and a detail message of {@code (cause==null ? null : cause.toString())}
   * <br/>(which typically contains the class and detail message of {@code cause})
   * <br/>This constructor is useful for runtime exceptions that are little more than wrappers for other throwables.
   *
   * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
   *              (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown)
   */
  public SQLRuntimeException(Throwable cause) {
	super(cause);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getMessage() {
	return message;
  }
}
