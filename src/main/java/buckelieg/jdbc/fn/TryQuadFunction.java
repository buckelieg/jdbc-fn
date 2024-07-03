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
 * Four-argument function with returned result that might throw an exception
 * <br/>There is no requirement that a new or distinct result be returned each time the function is invoked
 * <br/>This is a <a href="package-summary.html">functional interface</a> whose functional method is {@link #apply(Object, Object, Object, Object)}
 *
 * @param <I1> first input argument type
 * @param <I2> second input argument type
 * @param <I3> third input argument type
 * @param <I4> fourth input argument type
 * @param <O>  result type
 * @param <E>  an exception type thrown
 */
@FunctionalInterface
public interface TryQuadFunction<I1, I2, I3, I4, O, E extends Throwable> {


  /**
   * Represents a four-argument function which returns a result and might throw an Exception
   *
   * @param input1 first argument
   * @param input2 second argument
   * @param input3 third argument
   * @param input4 fourth argument
   * @return output
   * @throws E an exception
   */
  O apply(I1 input1, I2 input2, I3 input3, I4 input4) throws E;

  /**
   * Returns a composed function that first applies this function to
   * its input, and then applies the {@code after} function to the result.
   * If evaluation of either function throws an exception, it is relayed to
   * the caller of the composed function.
   *
   * @param <R>   the type of output of the {@code after} function, and of the
   *              composed function
   * @param after the function to apply after this function is applied
   * @return a composed function that first applies this function and then
   * applies the {@code after} function
   * @throws E                    an exception
   * @throws NullPointerException if <code>after</code>> is null
   */
  default <R> TryQuadFunction<I1, I2, I3, I4, R, E> andThen(TryFunction<? super O, ? extends R, E> after) throws E {
	if (null == after) throw new NullPointerException("after Function must be provided");
	return (I1 input1, I2 input2, I3 input3, I4 input4) -> after.apply(apply(input1, input2, input3, input4));
  }

  /**
   * Returns reference of lambda expression
   *
   * @param tryQuadFunction a function
   * @return lambda as a {@link TryQuadFunction} reference
   * @throws NullPointerException if <code>tryQuadFunction</code> is null
   */
  static <I1, I2, I3, I4, O, E extends Throwable> TryQuadFunction<I1, I2, I3, I4, O, E> of(TryQuadFunction<I1, I2, I3, I4, O, E> tryQuadFunction) {
	return requireNonNull(tryQuadFunction, "Function must be provided");
  }

}
