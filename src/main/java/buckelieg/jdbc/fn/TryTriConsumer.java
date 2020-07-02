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

import static java.util.Objects.requireNonNull;

/**
 * @param <I1>
 * @param <I2>
 * @param <I3>
 * @param <E> exception type
 */
@FunctionalInterface
public interface TryTriConsumer<I1, I2, I3, E extends Throwable> {

    /**
     * @param i1
     * @param i2
     * @param i3
     * @throws E an arbitrary exception
     */
    void accept(I1 i1, I2 i2, I3 i3) throws E;

    /**
     * Returns reference of lambda expression
     *
     * @param triConsumer a triConsumer function
     * @param <I1>
     * @param <I2>
     * @param <I3>
     * @param <E> exception type
     * @return lambda as {@link TryTriConsumer} reference
     * @throws NullPointerException if tryBiConsumer is null
     */
    static <I1, I2, I3, E extends Throwable> TryTriConsumer<I1, I2, I3, E> of(TryTriConsumer<I1, I2, I3, E> triConsumer) {
        return requireNonNull(triConsumer);
    }

}
