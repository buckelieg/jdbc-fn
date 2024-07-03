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
import java.util.Objects;

/**
 * Represents a predicate (boolean-valued function) of two arguments<br/>
 * This is two-arity specialization of {@link TryPredicate}.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #test(Object, Object)}.
 *
 * @param <I1> first argument parameter type
 * @param <I2> second argument parameter type
 * @param <E>  exception type
 * @see TryPredicate
 */
@FunctionalInterface
public interface TryBiPredicate<I1, I2, E extends Throwable> {

  /**
   * A constant that stands for {@code true}
   */
  TryBiPredicate<?, ?, ? extends Throwable> TRUE = (input1, input2) -> true;

  /**
   * A constant that stands for {@code false}
   */
  TryBiPredicate<?, ?, ? extends Throwable> FALSE = (input1, input2) -> false;

  /**
   * @param <I1> first argument parameter type
   * @param <I2> second argument parameter type
   * @param <E>  exception type
   * @return a type-checked {@linkplain #TRUE} constant
   */
  @Nonnull
  @SuppressWarnings("unchecked")
  static <I1, I2, E extends Throwable> TryBiPredicate<I1, I2, E> TRUE() {
	return (TryBiPredicate<I1, I2, E>) TRUE;
  }

  /**
   * @param <I1> first input argument type
   * @param <I2> second input argument type
   * @param <E>  exception type
   * @return a type-checked {@linkplain #FALSE} constant
   */
  @Nonnull
  @SuppressWarnings("unchecked")
  static <I1, I2, E extends Throwable> TryBiPredicate<I1, I2, E> FALSE() {
	return (TryBiPredicate<I1, I2, E>) FALSE;
  }

  /**
   * Evaluates this predicate on the given arguments
   *
   * @param i1 first input argument
   * @param i2 second input argument
   * @return {@code true} if the input arguments match the predicate,
   * otherwise {@code false}
   */
  boolean test(I1 i1, I2 i2) throws E;

  /**
   * Returns a composed predicate that represents a short-circuiting logical
   * AND of this predicate and another.  When evaluating the composed
   * predicate, if this predicate is {@code false}, then the {@code other}
   * predicate is not evaluated.
   *
   * <p>Any exceptions thrown during evaluation of either predicate are relayed
   * to the caller; if evaluation of this predicate throws an exception, the
   * {@code other} predicate will not be evaluated.
   *
   * @param other a predicate that will be logically-ANDed with this
   *              predicate
   * @return a composed predicate that represents the short-circuiting logical
   * AND of this predicate and the {@code other} predicate
   * @throws NullPointerException if other is null
   */
  default TryBiPredicate<I1, I2, E> and(TryBiPredicate<? super I1, ? super I2, E> other) throws E {
	if (null == other) throw new NullPointerException("other Predicate must be provided");
	return (I1 i1, I2 i2) -> test(i1, i2) && other.test(i1, i2);
  }

  /**
   * Logically negates this predicate
   *
   * @return a predicate that represents the logical negation of this
   * predicate
   */
  default TryBiPredicate<I1, I2, E> negate() {
	return (I1 i1, I2 i2) -> !test(i1, i2);
  }

  /**
   * Returns a composed predicate that represents a short-circuiting logical
   * OR of this predicate and another.  When evaluating the composed
   * predicate, if this predicate is {@code true}, then the {@code other}
   * predicate is not evaluated.
   *
   * <p>Any exceptions thrown during evaluation of either predicate are relayed
   * to the caller; if evaluation of this predicate throws an exception, the
   * {@code other} predicate will not be evaluated.
   *
   * @param other a predicate that will be logically-ORed with this predicate
   * @return a composed predicate that represents the short-circuiting logical
   * OR of this predicate and the {@code other} predicate
   * @throws NullPointerException if other is null
   */
  default TryBiPredicate<I1, I2, E> or(TryBiPredicate<? super I1, ? super I2, E> other) throws E {
	if (null == other) throw new NullPointerException("other Predicate must be provided");
	return (I1 i1, I2 i2) -> test(i1, i2) || other.test(i1, i2);
  }

  /**
   * Returns reference of lambda expression
   *
   * @param tryBiPredicate a biPredicate function
   * @param <I1>           first argument parameter type
   * @param <I2>           second argument parameter type
   * @param <E>            exception type
   * @return lambda as {@link TryBiPredicate} reference
   */
  static <I1, I2, E extends Throwable> TryBiPredicate<I1, I2, E> of(TryBiPredicate<I1, I2, E> tryBiPredicate) {
	return Objects.requireNonNull(tryBiPredicate, "Predicate must be provided");
  }
}

