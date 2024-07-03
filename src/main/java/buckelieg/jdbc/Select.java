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

import buckelieg.jdbc.fn.*;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

/**
 * An abstraction for SELECT statement
 */
@SuppressWarnings("unchecked")
@NotThreadSafe
@ParametersAreNonnullByDefault
public interface Select extends Query<Select> {

  /**
   * The N+1 problem resolution.
   * <br/>Represents a {@code Select} statement which results will be processed in batch mode
   *
   * @param <T> a type of processed entity
   */
  interface ForBatch<T> {

	/**
	 * Sets a size of a batch that will be used to generates chunks with
	 * <br/>Negative value of this parameter is silently ignored
	 *
	 * @param batchSize a size of a batch (must be positive number). Default value is {@code 1}
	 * @return select for batch processing query abstraction
	 */
	@Nonnull
	ForBatch<T> size(int batchSize);

	/**
	 * Executes this SELECT statement applying a {@code batchProcessor} function to each chunk<br/>
	 * <br/><table>
	 * <caption>A {@code batchProcessor} function parameters</caption>
	 * <tr>
	 * <th>Argument</th>
	 * <th>Type</th>
	 * <th>Description</th>
	 * </tr>
	 * <tr>
	 * <td>batch</td>
	 * <td>{@linkplain List}</td>
	 * <td>a list of items sliced by the {@linkplain #size(int)} value</td>
	 * </tr>
	 * </table>
	 *
	 * @param batchProcessor a batch processor function
	 * @return a {@linkplain Stream} of resulting (post-processed) items
	 */
	@Nonnull
	default Stream<T> execute(TryConsumer<List<T>, ? extends Exception> batchProcessor) {
	  if (null == batchProcessor) throw new NullPointerException("Batch processor function must be provided");
	  return execute((batch, session) -> batchProcessor.accept(batch));
	}

	/**
	 * Executes this SELECT statement applying a {@code batchProcessor} function to each chunk<br/>
	 * <br/><table>
	 * <caption>A {@code batchProcessor} function parameters</caption>
	 * <tr>
	 * <th>Argument</th>
	 * <th>Type</th>
	 * <th>Description</th>
	 * </tr>
	 * <tr>
	 * <td>batch</td>
	 * <td>{@linkplain List}</td>
	 * <td>a list of items sliced by the {@linkplain #size(int)} value</td>
	 * </tr>
	 * <tr>
	 * <td>session</td>
	 * <td>{@linkplain Session}</td>
	 * <td>a query session bound to this implicitly created transaction</td>
	 * </tr>
	 * </table>
	 *
	 * @param batchProcessor a batch processor function
	 * @return a {@linkplain Stream} of resulting (post-processed) items
	 */
	@Nonnull
	default Stream<T> execute(TryBiConsumer<List<T>, Session, ? extends Exception> batchProcessor) {
	  if (null == batchProcessor) throw new NullPointerException("Batch processor function must be provided");
	  return execute((batch, session, batchIndex) -> batchProcessor.accept(batch, session));
	}

	/**
	 * Executes this SELECT statement applying a {@code batchProcessor} function to each chunk<br/>
	 * <br/><table>
	 * <caption>A {@code batchProcessor} function parameters</caption>
	 * <tr>
	 * <th>Argument</th>
	 * <th>Type</th>
	 * <th>Description</th>
	 * </tr>
	 * <tr>
	 * <td>batch</td>
	 * <td>{@linkplain List}</td>
	 * <td>a list of items sliced by the {@linkplain #size(int)} value</td>
	 * </tr>
	 * <tr>
	 * <td>session</td>
	 * <td>{@linkplain Session}</td>
	 * <td>a query session bound to this implicitly created transaction</td>
	 * </tr>
	 * <tr>
	 * <td>index</td>
	 * <td>{@linkplain Integer}</td>
	 * <td>current batch number being processed</td>
	 * </tr>
	 * </table>
	 *
	 * @param batchProcessor a batch processor function
	 * @return a {@linkplain Stream} of resulting (post-processed) items
	 */
	@Nonnull
	Stream<T> execute(TryTriConsumer<List<T>, Session, Integer, ? extends Exception> batchProcessor);

  }

  /**
   * Executes this SELECT statement applying a {@code batchProcessor} function to each chunk<br/>
   * <br/><table>
   * <caption>a {@code mapper} function parameters</caption>
   * <tr>
   * <th>Argument</th>
   * <th>Type</th>
   * <th>Description</th>
   * </tr>
   * <tr>
   * <td>rs</td>
   * <td>{@linkplain ValueReader}</td>
   * <td>a convenient wrapper for {@linkplain ResultSet} for value read</td>
   * </tr>
   * <tr>
   * <td>index</td>
   * <td>{@linkplain Integer}</td>
   * <td>current underlying {@linkplain ResultSet} number being processed</td>
   * </tr>
   * </table>
   *
   * @param mapper a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
   * @param <T>    an element type
   * @return select for batch processing query abstraction
   */
  @Nonnull
  <T> ForBatch<T> forBatch(TryBiFunction<ValueReader, Integer, T, SQLException> mapper);

  /**
   * @param mapper a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
   * @param <T>    an element type
   * @return select for batch processing query abstraction
   */
  @Nonnull
  default <T> ForBatch<T> forBatch(TryFunction<ValueReader, T, SQLException> mapper) {
	if (null == mapper) throw new NullPointerException("Mapper must be provided");
	return forBatch((rs, i) -> mapper.apply(rs));
  }

  /**
   * @return select for batch processing query abstraction
   */
  @Nonnull
  default ForBatch<Map<String, Object>> forBatch() {
	return forBatch(JDBCDefaults::defaultMapper);
  }

  /**
   * Retrieves a metadata for this SELECT statement<br/>
   * <br/><table>
   * <caption>a {@code mapper} function parameters</caption>
   * <tr>
   * <th>Argument</th>
   * <th>Type</th>
   * <th>Description</th>
   * </tr>
   * <tr>
   * <td>meta</td>
   * <td>{@linkplain Metadata}</td>
   * <td>a convenient wrapper for {@linkplain ResultSet} metadata read</td>
   * </tr>
   * </table>
   *
   * @param mapper {@linkplain Metadata} mapper
   * @return a {@linkplain Metadata} for this query
   * @throws NullPointerException if {@code mapper} is null
   */
  @Nonnull
  <T> T forMeta(Function<Metadata, T> mapper);

  /**
   * In cases when single result of SELECT statement is expected
   * <br/>Like <code>SELECT COUNT(*) FROM TABLE_NAME</code> etc<br/>
   * <br/><table>
   * <caption>a {@code mapper} function parameters</caption>
   * <tr>
   * <th>Argument</th>
   * <th>Type</th>
   * <th>Description</th>
   * </tr>
   * <tr>
   * <td>rs</td>
   * <td>{@linkplain ValueReader}</td>
   * <td>a convenient wrapper for {@linkplain ResultSet} for value read</td>
   * </tr>
   * </table>
   *
   * @param mapper a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
   * @throws NullPointerException if <code>mapper</code> is null
   * @see #execute(TryFunction)
   */
  @Nonnull
  default <T> Optional<T> single(TryFunction<ValueReader, T, SQLException> mapper) {
	T result;
	try {
	  result = fetchSize(1).maxRows(1).execute(mapper).collect(Collectors.toList()).iterator().next();
	} catch (NoSuchElementException e) {
	  result = null;
	} catch (Exception e) {
	  throw SQLRuntimeException.class.isAssignableFrom(e.getClass()) ? Utils.newSQLRuntimeException(e) : new RuntimeException(e);
	}
	return ofNullable(result);
  }


  /**
   * Executes SELECT statement for SINGLE result with default mapper applied
   *
   * @return an {@link Optional} with the {@link Map} as a value
   */
  @Nonnull
  default Optional<Map<String, Object>> single() {
	return single(JDBCDefaults::defaultMapper);
  }

  /**
   * Executes this SELECT statement returning a {@code Stream} of mapped values as {@code Map}s
   *
   * @return a {@link Stream} of {@link Map}s
   * @see #execute(TryFunction)
   */
  @Nonnull
  default Stream<Map<String, Object>> execute() {
	return execute(JDBCDefaults::defaultMapper);
  }

  /**
   * <table>
   *     <caption>a {@code mapper} function parameters</caption>
   *     <tr>
   *         <th>Argument</th>
   *         <th>Type</th>
   *         <th>Description</th>
   *     </tr>
   *     <tr>
   *         <td>rs</td>
   *         <td>{@linkplain ValueReader}</td>
   *         <td>a convenient wrapper for {@linkplain ResultSet} for value read</td>
   *     </tr>
   *     <tr>
   *         <td>index</td>
   *         <td>{@linkplain Integer}</td>
   *         <td>current underlying {@linkplain ResultSet} number being processed</td>
   *     </tr>
   * </table>
   *
   * @param mapper a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
   * @param <T>    item type
   * @return a {@link Stream} over mapped {@link ResultSet}
   * @throws NullPointerException if <code>mapper</code> is null
   */
  @Nonnull
  <T> Stream<T> execute(TryBiFunction<ValueReader, Integer, T, SQLException> mapper);

  /**
   * Executes this SELECT statement returning a {@code Stream} of mapped values over {@code ResultSet} object<br/>
   * <br/><table>
   * <caption>a {@code mapper} function parameters</caption>
   * <tr>
   * <th>Argument</th>
   * <th>Type</th>
   * <th>Description</th>
   * </tr>
   * <tr>
   * <td>rs</td>
   * <td>{@linkplain ValueReader}</td>
   * <td>a convenient wrapper for {@linkplain ResultSet} for value read</td>
   * </tr>
   * </table>
   *
   * @param mapper a {@link ResultSet} mapper function which is not required to handle {@link SQLException}
   * @return a {@link Stream} over mapped {@link ResultSet}
   * @throws NullPointerException if mapper is null
   * @throws SQLRuntimeException  as a wrapper for {@link SQLException}
   * @throws NullPointerException if <code>mapper</code> is null
   * @see #execute()
   * @see ValueReader
   */
  @Nonnull
  default <T> Stream<T> execute(TryFunction<ValueReader, T, SQLException> mapper) {
	if (null == mapper) throw new NullPointerException("Mapper must be provided");
	return execute((rs, i) -> mapper.apply(rs));
  }

  /**
   * Configures {@link java.sql.Statement} fetch size parameter<br/>
   * Default value is <code>15</code>
   *
   * @param size desired fetch size. Should be greater than <code>0</code>
   * @return select query abstraction
   * @implNote Default value is <code>15</code>
   * @see java.sql.Statement#setFetchSize(int)
   * @see ResultSet#setFetchSize(int)
   */
  @Nonnull
  Select fetchSize(int size);

  /**
   * Updates max rows obtained with this query
   *
   * @param max rows number limit
   * @return select query abstraction
   * @see java.sql.Statement#setMaxRows(int)
   */
  @Nonnull
  Select maxRows(int max);

  /**
   * Updates max rows obtained with this query
   *
   * @param max rows number limit
   * @return select query abstraction
   * @see java.sql.Statement#setLargeMaxRows(long)
   */
  @Nonnull
  Select maxRows(long max);
}
