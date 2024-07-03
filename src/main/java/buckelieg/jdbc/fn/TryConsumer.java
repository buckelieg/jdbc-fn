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
 * One-argument function which returns no result that might throw an exception
 * <br/>This is a <a href="package-summary.html">functional interface</a> whose functional method is {@link #accept(Object)}
 *
 * @param <I> the type of the input to the operation
 * @param <E> the type of the possible exception
 */
@FunctionalInterface
public interface TryConsumer<I, E extends Throwable> {

  /**
   * A <b>NO OP</b>eration constant
   */
  TryConsumer<?, ? extends Throwable> NOOP = input -> {};

  /**
   * A type-checked <b>NO OP</b>eration
   *
   * @param <I> the input argument type
   * @param <E> an exception type
   * @return a type-checked {@linkplain #NOOP} constant
   */
  @Nonnull
  @SuppressWarnings("unchecked")
  static <I, E extends Throwable> TryConsumer<I, E> NOOP() {
	return (TryConsumer<I, E>) NOOP;
  }

  /**
   * A one-argument function which returns no results and might throw an Exception
   *
   * @param i the input argument
   * @throws E an exception
   */
  void accept(I i) throws E;

  /**
   * Returns reference of lambda expression
   * <br/>Typical usage is:
   * <br/>{@code TryBiConsumer.of(x -> {}).andThen(x -> {});}
   * @param tryConsumer a consumer
   * @return {@link TryConsumer} reference
   * @throws NullPointerException if <code>tryConsumer</code> is null
   */
  static <I, E extends Throwable> TryConsumer<I, E> of(TryConsumer<I, E> tryConsumer) {
	return requireNonNull(tryConsumer, "Consumer must be provided");
  }

  /**
   * Returns a composed {@code TryConsumer} that performs, in sequence, this
   * <br/>operation followed by the {@code after} operation. If performing either
   * <br/>operation throws an exception, then corresponding exception is thrown
   * <br/>If performing this operation throws an exception,
   * <br/>the {@code after} operation will not be performed
   *
   * @param after the operation to perform after this operation
   * @return a composed {@code TryConsumer} that performs in sequence this
   * operation followed by the {@code after} operation
   * @throws E                    an exception
   * @throws NullPointerException if {@code after} is null
   */
  default TryConsumer<I, E> andThen(TryConsumer<? super I, E> after) throws E {
	if (null == after) throw new NullPointerException("after Consumer must be provided");
	return (I i) -> {
	  accept(i);
	  after.accept(i);
	};
  }

  /**
   * Returns a composed {@code TryConsumer} that performs, in sequence, this
   * <br/>operation is preceded by the {@code before} operation. If performing either
   * <br/>operation throws an exception, then corresponding exception is thrown
   * <br/>If performing this operation throws an exception,
   * <br/>the {@code before} operation will not be performed
   *
   * @param before the operation to perform before this operation
   * @return a composed {@code TryConsumer} that performs in sequence this
   * operation followed by the {@code before} operation
   * @throws E                    an exception
   * @throws NullPointerException if {@code before} is null
   */
  default TryConsumer<I, E> compose(TryConsumer<? super I, E> before) throws E {
	if (null == before) throw new NullPointerException("before Consumer must be provided");
	return (I i) -> {
	  before.accept(i);
	  accept(i);
	};
  }

}
