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
package buckelieg.jdbc;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.reflect.Array.get;
import static java.lang.reflect.Array.getLength;

final class BoxedPrimitiveIterable implements Iterable<Number> {

  private final Object array;
  private final int length;

  BoxedPrimitiveIterable(Object array) {
	this.array = array;
	this.length = getLength(array);
  }

  @Nonnull
  @Override
  public Iterator<Number> iterator() {
	return new Iterator<Number>() {

	  private final AtomicInteger currentIndex = new AtomicInteger();

	  @Override
	  public boolean hasNext() {
		return currentIndex.get() < length;
	  }

	  @Override
	  public Number next() {
		return (Number) get(array, currentIndex.getAndIncrement());
	  }
	};
  }
}
