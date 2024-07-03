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
 * No-argument function which return no result that might throw an exception
 * <br/>This is a <a href="package-summary.html">functional interface</a> whose functional method is {@link #run()}
 *
 * @param <E> exception type
 */
@FunctionalInterface
public interface TryRunnable<E extends Throwable> {

  /**
   * A <b>NO OP</b>eration constant
   */
  TryRunnable<? extends Throwable> NOOP = () -> {};

  /**
   * A type-checked <b>NO OP</b>eration
   *
   * @param <E> the type of the exception thrown
   * @return a type-checked {@linkplain #NOOP} constant
   */
  @Nonnull
  @SuppressWarnings("unchecked")
  static <E extends Throwable> TryRunnable<E> NOOP() {
	return (TryRunnable<E>) NOOP;
  }

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

}
