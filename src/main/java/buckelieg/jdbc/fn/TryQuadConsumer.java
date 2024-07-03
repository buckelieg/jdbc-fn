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
 * Represents an operation that accepts four input arguments and returns no
 * result. This is the four-arity specialization of {@link TryConsumer}.
 * Unlike most other functional interfaces, {@code TryQuadConsumer} is expected
 * to operate via side effects.
 *
 * @param <I1> first argument parameter type
 * @param <I2> second argument parameter type
 * @param <I3> third argument parameter type
 * @param <I4> fourth argument parameter type
 * @param <E>  exception type
 */
@FunctionalInterface
public interface TryQuadConsumer<I1, I2, I3, I4, E extends Throwable> {

  /**
   * A <b>NO OP</b>eration constant
   */
  TryQuadConsumer<?, ?, ?, ?, ? extends Throwable> NOOP = (input1, input2, input3, input4) -> {};

  /**
   * A type-checked <b>NO OP</b>eration
   *
   * @param <I1> first argument parameter type
   * @param <I2> second argument parameter type
   * @param <I3> third argument parameter type
   * @param <I4> fourth argument parameter type
   * @param <E>  exception type
   * @return a type-checked {@linkplain #NOOP} constant
   */
  @Nonnull
  @SuppressWarnings("unchecked")
  static <I1, I2, I3, I4, E extends Throwable> TryQuadConsumer<I1, I2, I3, I4, E> NOOP() {
	return (TryQuadConsumer<I1, I2, I3, I4, E>) NOOP;
  }

  /**
   * A four-argument function which returns no results and might throw an Exception
   *
   * @param input1 first argument
   * @param input2 second argument
   * @param input3 third argument
   * @param input4 fourth argument
   * @throws E an arbitrary exception
   */
  void accept(I1 input1, I2 input2, I3 input3, I4 input4) throws E;

  /**
   * Returns reference of lambda expression
   *
   * @param tryQuadConsumer a consumer
   * @return lambda as a {@link TryQuadConsumer} reference
   * @throws NullPointerException if <code>tryQuadConsumer</code> is null
   */
  static <I1, I2, I3, I4, E extends Throwable> TryQuadConsumer<I1, I2, I3, I4, E> of(TryQuadConsumer<I1, I2, I3, I4, E> tryQuadConsumer) {
	return Objects.requireNonNull(tryQuadConsumer, "Consumer must be provided");
  }

}
