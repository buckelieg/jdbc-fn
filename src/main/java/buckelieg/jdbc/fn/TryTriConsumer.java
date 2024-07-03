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

import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Represents an operation that accepts three input arguments and returns no
 * result. This is the three-arity specialization of {@link TryConsumer}.
 * Unlike most other functional interfaces, {@code TryTriConsumer} is expected
 * to operate via side effects.
 *
 * @param <I1> first argument type
 * @param <I2> second argument type
 * @param <I3> third argument type
 * @param <E>  exception type
 */
@FunctionalInterface
public interface TryTriConsumer<I1, I2, I3, E extends Throwable> {

  /**
   * A <b>NO OP</b>eration constant
   */
  TryTriConsumer<?, ?, ?, ? extends Throwable> NOOP = (input1, input2, input3) -> {};

  /**
   * A type-checked <b>NO OP</b>eration
   *
   * @param <I1> first argument type
   * @param <I2> second argument type
   * @param <I3> third argument type
   * @param <E>  exception type
   * @return a type-checked {@linkplain #NOOP} constant
   */
  @Nonnull
  @SuppressWarnings("unchecked")
  static <I1, I2, I3, E extends Throwable> TryTriConsumer<I1, I2, I3, E> NOOP() {
	return (TryTriConsumer<I1, I2, I3, E>) NOOP;
  }

  /**
   * A three-argument function which returns no results and might throw an Exception
   *
   * @param input1 first argument
   * @param input2 second argument
   * @param input3 third argument
   * @throws E an arbitrary exception
   */
  void accept(I1 input1, I2 input2, I3 input3) throws E;

  /**
   * Returns reference of lambda expression
   *
   * @param triConsumer a triConsumer function
   * @param <I1>        first argument type
   * @param <I2>        second argument type
   * @param <I3>        third argument type
   * @param <E>         exception type
   * @return lambda as {@link TryTriConsumer} reference
   * @throws NullPointerException if <code>tryBiConsumer</code> is null
   */
  static <I1, I2, I3, E extends Throwable> TryTriConsumer<I1, I2, I3, E> of(TryTriConsumer<I1, I2, I3, E> triConsumer) {
	return requireNonNull(triConsumer, "Consumer must be provided");
  }

}
