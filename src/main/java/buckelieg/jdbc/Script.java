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

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * An abstraction for a series of an separate arbitrary SQL queries which are executed sequentially
 * <br/>Result is an execution time (in milliseconds) taken the whole series to complete
 */
@SuppressWarnings("unchecked")
@ParametersAreNonnullByDefault
public interface Script extends Query {

    /**
     * Executes a SQL script
     *
     * @return script execution time in milliseconds
     */
    @Nonnull
    Long execute();

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    default Script timeout(int timeout) {
        return timeout(timeout, TimeUnit.SECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    Script timeout(int timeout, TimeUnit unit);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    Script escaped(boolean escapeProcessing);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    Script poolable(boolean poolable);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    Script print(Consumer<String> printer);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    default Script print() {
        return print(System.out::println);
    }

    /**
     * Sets flag whether to skip errors during script execution
     * <br/>If flag is set to false then script execution is halt on the first error occurred
     *
     * @param skipErrors false if to stop on the first error, true (the default) - otherwise
     * @return script query abstraction
     */
    @Nonnull
    Script skipErrors(boolean skipErrors);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    Script skipWarnings(boolean skipWarnings);

    /**
     * Registers an error/warning handler
     * <br/>The default handler is noop handler
     * <br/>I.e. if skipErrors or skipWarnings flag is set to false but no errorHandler is provided the default handler (which does nothing) is used
     *
     * @param handler error/warning handler
     * @return script query abstraction
     * @throws NullPointerException if handler is null
     * @see #skipErrors(boolean)
     * @see #skipWarnings(boolean)
     */
    @Nonnull
    Script errorHandler(Consumer<SQLException> handler);

    /**
     * Sets handler for each parsed query (as SQL string) to be handled
     * <br/>Intended for debug purposes (like Logger::debug)
     *
     * @param logger query string consumer
     * @return a script query abstraction
     */
    @Nonnull
    Script verbose(Consumer<String> logger);

    /**
     * Prints each parsed query (as SQL) to standard output
     *
     * @return a script query abstraction
     * @see #verbose(Consumer)
     * @see System#out
     * @see PrintStream#println(String)
     */
    @Nonnull
    default Script verbose() {
        return verbose(System.out::println);
    }

}
