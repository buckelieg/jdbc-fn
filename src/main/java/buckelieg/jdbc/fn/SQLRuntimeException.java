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
package buckelieg.jdbc.fn;

/**
 * Represents a {@link RuntimeException} wrapper for {@link java.sql.SQLException}
 *
 * @see java.sql.SQLException
 * @see RuntimeException
 */
public class SQLRuntimeException extends RuntimeException {

    private String message;

    /**
     * {@inheritDoc}
     */
    public SQLRuntimeException() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    public SQLRuntimeException(String message) {
        super(message);
    }

    /**
     * Constructs a new runtime exception with provided string as its detail message
     * <br/>If <code>fillInStackTrace</code> parameter is set to <code>true</code> then stack trace is filled in, otherwise - not
     *
     * @param message exception message string
     * @param fillInStackTrace whether to fill in stack trace or not
     */
    public SQLRuntimeException(String message, boolean fillInStackTrace) {
        this.message = message;
        if (fillInStackTrace) {
            fillInStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     */
    public SQLRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * {@inheritDoc}
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
