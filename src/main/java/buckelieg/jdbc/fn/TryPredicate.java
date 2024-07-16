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
 * Represents a predicate (boolean-valued function) of one argument
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #test(Object)}.
 *
 * @param <I> the type of the input to the predicate
 * @param <E> an exception type
 */
@FunctionalInterface
public interface TryPredicate<I, E extends Throwable> {

  /**
   * A constant that stands for {@code true}
   */
  TryPredicate<?, ? extends Throwable> TRUE = input -> true;

  /**
   * A constant that stands for {@code false}
   */
  TryPredicate<?, ? extends Throwable> FALSE = input -> false;

  /**
   * @param <I> input argument type
   * @param <E> exception type
   * @return a type-checked {@linkplain #TRUE} constant
   */
  @Nonnull
  @SuppressWarnings("unchecked")
  static <I, E extends Throwable> TryPredicate<I, E> TRUE() {
	return (TryPredicate<I, E>) TRUE;
  }

  /**
   * @param <I> input argument type
   * @param <E> exception type
   * @return a type-checked {@linkplain #FALSE} constant
   */
  @Nonnull
  @SuppressWarnings("unchecked")
  static <I, E extends Throwable> TryPredicate<I, E> FALSE() {
	return (TryPredicate<I, E>) FALSE;
  }

  /**
   * Evaluates this predicate on the given argument
   *
   * @param input the input argument
   * @return {@code true} if the input argument matches the predicate,
   * otherwise {@code false}
   * @throws E an arbitrary exception
   */
  boolean test(I input) throws E;

  /**
   * Returns a composed predicate that represents a short-circuiting logical
   * AND of this predicate and another. When evaluating the composed
   * predicate, if this predicate is {@code false}, then the {@code other}
   * predicate is not evaluated.
   *
   * <p>Any exceptions thrown during evaluation of either predicate are relayed
   * to the caller; if evaluation of this predicate throws an exception, the
   * {@code other} predicate will not be evaluated.
   *
   * @param other a predicate that will be logically-ANDed with this
   *              predicate
   * @return a composed predicate that represents the short-circuiting logical AND of this predicate and the {@code other} predicate
   * @throws NullPointerException if <code>other</code> is null
   */
  default TryPredicate<I, E> and(TryPredicate<? super I, E> other) throws E {
	if (null == other) throw new NullPointerException("other Predicate must be provided");
	return i -> test(i) && other.test(i);
  }

  /**
   * Logically negates this predicate
   *
   * @return a predicate that represents the logical negation of this
   * predicate
   */
  default TryPredicate<I, E> negate() {
	return i -> !test(i);
  }

  /**
   * Returns a composed predicate that represents a short-circuiting logical
   * OR of this predicate and another. When evaluating the composed
   * predicate, if this predicate is {@code true}, then the {@code other}
   * predicate is not evaluated.
   *
   * <p>Any exceptions thrown during evaluation of either predicate are relayed
   * to the caller; if evaluation of this predicate throws an exception, the
   * {@code other} predicate will not be evaluated.
   *
   * @param other a predicate that will be logically-ORed with this
   *              predicate
   * @return a composed predicate that represents the short-circuiting logical OR of this predicate and the {@code other} predicate
   * @throws E                    an arbitrary exception
   * @throws NullPointerException if <code>other</code> is null
   */
  default TryPredicate<I, E> or(TryPredicate<? super I, E> other) throws E {
	if (null == other) throw new NullPointerException("other Predicate must be provided");
	return i -> test(i) || other.test(i);
  }

  /**
   * Returns a predicate that tests if two arguments are equal according
   * to {@link Objects#equals(Object, Object)}.
   *
   * @param <I>       the type of arguments to the predicate
   * @param targetRef the object reference with which to compare for equality,
   *                  which may be {@code null}
   * @return a predicate that tests if two arguments are equal according to {@link Objects#equals(Object, Object)}
   * @throws E an arbitrary exception
   */
  static <I, E extends Throwable> TryPredicate<I, E> isEqual(Object targetRef) throws E {
	return (null == targetRef) ? Objects::isNull : ref -> Objects.equals(ref, targetRef);
  }

  /**
   * Returns a predicate that is the negation of the supplied predicate.
   * This is accomplished by returning result of the calling
   * {@code target.negate()}.
   *
   * @param <I>    the type of arguments to the specified predicate
   * @param target predicate to negate
   * @return a predicate that negates the results of the supplied
   * predicate
   * @throws E                    an arbitrary exception
   * @throws NullPointerException if target is null
   */
  @SuppressWarnings("unchecked")
  static <I, E extends Throwable> TryPredicate<I, E> not(TryPredicate<? super I, E> target) throws E {
	if (null == target) throw new NullPointerException("target Predicate must be provided");
	return (TryPredicate<I, E>) target.negate();
  }

  /**
   * Returns reference of lambda expression
   *
   * @param tryPredicate a predicate function
   * @param <I>          the type of the input to the predicate
   * @param <E>          an arbitrary exception
   * @return lambda as {@link TryPredicate} reference
   */
  static <I, E extends Throwable> TryPredicate<I, E> of(TryPredicate<I, E> tryPredicate) {
	return Objects.requireNonNull(tryPredicate, "Predicate must be provided");
  }
}
