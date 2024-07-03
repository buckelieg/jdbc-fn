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
 * One-argument function which returns a result that might throw an exception
 * <br/>There is no requirement that a new or distinct result be returned each time the function is invoked.
 * <br/>This is a <a href="package-summary.html">functional interface</a> whose functional method is {@link #apply(Object)}}.
 *
 * @param <I> argument type
 * @param <O> result type
 * @param <E> an exception type thrown
 */
@FunctionalInterface
public interface TryFunction<I, O, E extends Throwable> {

    /**
     * Represents a one-argument function that might throw an Exception
     *
     * @param input function input.
     * @return mapped value
     * @throws E in case of something went wrong
     */
    O apply(I input) throws E;

    /**
     * Returns a function that always returns its input argument
     *
     * @param <T> the type of the input and output objects to the function
     * @return a function that always returns its input argument
     */
    static <T, E extends Throwable> TryFunction<T, T, E> identity() {
        return t -> t;
    }

    /**
     * Returns reference of lambda expression
     * <br/>Typical usage is:
     * <br/>{@code TryFunction.of(x -> null).andThen(nil -> null);}
     *
     * @param tryFunction a function
     * @param <I>         argument type
     * @param <O>         result type
     * @param <E>         exception type
     * @return lambda as {@link TryFunction} reference
     * @throws NullPointerException if tryFunction is null
     */
    static <I, O, E extends Throwable> TryFunction<I, O, E> of(TryFunction<I, O, E> tryFunction) {
        return requireNonNull(tryFunction);
    }

    /**
     * Returns a composed function that first applies the {@code before} function to its input, and then applies this function to the result
     *
     * @param <V>    the type of input to the {@code before} function, and to the
     *               composed function
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     * @throws E                    an exception
     * @throws NullPointerException if <code>before</code> is null
     * @see #andThen(TryFunction)
     */
    default <V> TryFunction<V, O, E> compose(TryFunction<? super V, ? extends I, ? extends E> before) throws E {
        if (null == before) throw new NullPointerException("before Function must be provided");
        return (V v) -> apply(before.apply(v));
    }

    /**
     * Returns a composed function that first applies this function to its input, and then applies the {@code after} function to the result
     *
     * @param <V>   the type of output of the {@code after} function, and of the
     *              composed function
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     * @throws E                    an exception
     * @throws NullPointerException if <code>after</code> is null
     * @see #compose(TryFunction)
     */
    default <V> TryFunction<I, V, E> andThen(TryFunction<? super O, ? extends V, ? extends E> after) throws E {
        if (null == after) throw new NullPointerException("after Function must be provided");
        return (I t) -> after.apply(apply(t));
    }
}
