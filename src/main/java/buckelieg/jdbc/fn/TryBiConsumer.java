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
 * Represents an operation that accepts two input arguments and returns no
 * result. This is the two-arity specialization of {@link TryConsumer}.
 * Unlike most other functional interfaces, {@code TryBiConsumer} is expected
 * to operate via side effects.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #accept(Object, Object)}.
 *
 * @param <I1> the type of the first argument to the operation
 * @param <I2> the type of the second argument to the operation
 * @param <E>  the type of the exception thrown
 * @see TryConsumer
 */
@FunctionalInterface
public interface TryBiConsumer<I1, I2, E extends Throwable> {

  /**
   * A <b>NO OP</b>eration constant
   */
  TryBiConsumer<?, ?, ? extends Throwable> NOOP = (input1, input2) -> {};

  /**
   * A type-checked <b>NO OP</b>eration
   *
   * @param <I1> the type of the first argument to the operation
   * @param <I2> the type of the second argument to the operation
   * @param <E>  the type of the exception thrown
   * @return a type-checked {@linkplain #NOOP} constant
   */
  @Nonnull
  @SuppressWarnings("unchecked")
  static <I1, I2, E extends Throwable> TryBiConsumer<I1, I2, E> NOOP() {
	return (TryBiConsumer<I1, I2, E>) NOOP;
  }

  /**
   * A two-argument function which returns no results and might throw an Exception
   *
   * @param i1 the first input argument
   * @param i2 the second input argument
   * @throws E an exception
   */
  void accept(I1 i1, I2 i2) throws E;

  /**
   * Returns reference of lambda expression
   * <br/>Typical usage is:
   * <br/>{@code TryBiConsumer.of((x, y) -> {}).andThen((x, y) -> {});}
   *
   * @param tryBiConsumer a biConsumer function
   * @param <I1>          the type of the first argument to the operation
   * @param <I2>          the type of the second argument to the operation
   * @param <E>           the type of the exception thrown
   * @return lambda as {@link TryBiConsumer} reference
   * @throws NullPointerException if tryBiConsumer is null
   */
  static <I1, I2, E extends Throwable> TryBiConsumer<I1, I2, E> of(TryBiConsumer<I1, I2, E> tryBiConsumer) {
	return requireNonNull(tryBiConsumer, "Consumer must be provided");
  }

  /**
   * Returns a composed {@code BiConsumer} that performs, in sequence, this
   * operation followed by the {@code after} operation. If performing either
   * operation throws an exception, it is relayed to the caller of the
   * composed operation.  If performing this operation throws an exception,
   * the {@code after} operation will not be performed
   *
   * @param after the operation to perform after this operation
   * @return a composed {@code BiConsumer} that performs in sequence this
   * operation followed by the {@code after} operation
   * @throws E                    an exception
   * @throws NullPointerException if {@code after} is null
   */
  default TryBiConsumer<I1, I2, E> andThen(TryBiConsumer<? super I1, ? super I2, ? extends E> after) throws E {
	if (null == after) throw new NullPointerException("after Predicate must be provided");
	return (l, r) -> {
	  accept(l, r);
	  after.accept(l, r);
	};
  }
}
