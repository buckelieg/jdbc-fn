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
package buckelieg.jdbc.fn;

import static java.util.Objects.requireNonNull;

/**
 * No-argument function which returns a result that might throw an exception
 * <br/>There is no requirement that a new or distinct result be returned each time the function is invoked
 * <br/>This is a <a href="package-summary.html">functional interface</a> whose functional method is {@link #get()}
 *
 * @param <O> the type of results supplied by this supplier
 * @param <E> a type of an {@link Exception} that might be thrown
 */
@FunctionalInterface
public interface TrySupplier<O, E extends Throwable> {

    /**
     * Value supplier function which might throw an Exception
     *
     * @return an optional value
     * @throws E an exception if something went wrong
     */
    O get() throws E;

    /**
     * Returns reference of lambda expression
     *
     * @param trySupplier a supplier function
     * @return lambda as {@link TrySupplier} reference
     * @throws NullPointerException if <code>trySupplier</code> is null
     */
    static <O, E extends Throwable> TrySupplier<O, E> of(TrySupplier<O, E> trySupplier) {
        return requireNonNull(trySupplier, "Supplier must be provided");
    }

}
