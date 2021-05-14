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
 * No-argument function which return no result that might throw an exception
 * <br/>This is a <a href="package-summary.html">functional interface</a> whose functional method is {@link #run()}
 *
 * @param <E> exception type
 */
@FunctionalInterface
public interface TryRunnable<E extends Throwable> {

    /**
     * Performs an action with possible exceptional result
     *
     * @throws E an exception
     */
    void run() throws E;

    /**
     * Returns reference of lambda expression
     *
     * @param tryRunnable an action function
     * @return {@link TryRunnable} reference
     * @throws NullPointerException if tryAction is null
     */
    static <E extends Throwable> TryRunnable<E> of(TryRunnable<E> tryRunnable) {
        return requireNonNull(tryRunnable);
    }

    /**
     * Returns a composed {@code TryAction} that performs, in sequence, this<br/>
     * operation is followed by the {@code after} operation. If performing either<br/>
     * operation throws an exception, then corresponding exception is thrown<br/>
     * If performing this operation throws an exception,<br/>
     * the {@code after} operation will not be performed
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code TryAction} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws E                    an exception
     * @throws NullPointerException if {@code after} is null
     */
    default TryRunnable<E> andThen(TryRunnable<E> after) throws E {
        requireNonNull(after);
        return () -> {
            run();
            after.run();
        };
    }

    /**
     * Returns a composed {@code TryAction} that performs, in sequence, this<br/>
     * operation is preceded by the {@code before} operation. If performing either<br/>
     * operation throws an exception, then corresponding exception is thrown<br/>
     * If performing this operation throws an exception,<br/>
     * the {@code before} operation will not be performed
     *
     * @param before the operation to perform before this operation
     * @return a composed {@code TryAction} that performs in sequence this
     * operation followed by the {@code before} operation
     * @throws E                    an exception
     * @throws NullPointerException if {@code before} is null
     */
    default TryRunnable<E> compose(TryRunnable<E> before) throws E {
        requireNonNull(before);
        return () -> {
            before.run();
            run();
        };
    }

}
