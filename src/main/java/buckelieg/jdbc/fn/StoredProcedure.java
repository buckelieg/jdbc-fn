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
package buckelieg.jdbc.fn;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

/**
 * An abstraction for STORED PROCEDURE call statement.
 */
@ParametersAreNonnullByDefault
public interface StoredProcedure extends Select {

    /**
     * Calls procedure for results processing which are expected in the OUT/INOUT parameters.
     * <br/>If registered - these will be invoked AFTER result set is iterated over.
     * <br/>If the result set is not iterated exhaustively - mapper and (then) consumer will NOT be invoked.
     * <br/>The logic of this is to call mapper for creating result and the call consumer to process it.
     *
     * @param mapper   function for procedure call results processing
     * @param consumer mapper result consumer - will be called after mapper is finished
     * @return select query abstraction
     * @throws NullPointerException if mapper or consumer is null
     */
    @Nonnull
    <T> Select call(TryFunction<CallableStatement, T, SQLException> mapper, Consumer<T> consumer);

    /**
     * Whenever the stored procedure returns no result set but the own results only - this convenience shorthand may be called.
     *
     * @param mapper function that constructs from {@link CallableStatement}
     * @return mapped result as {@link Optional}
     * @throws NullPointerException if mapper is null
     * @see #call(TryFunction, Consumer)
     * @see Optional
     */
    @Nonnull
    default <T> Optional<T> call(TryFunction<CallableStatement, T, SQLException> mapper) {
        requireNonNull(mapper, "Mapper must be provided");
        List<Optional<T>> results = new ArrayList<>(1);
        call(cs -> ofNullable(mapper.apply(cs)), results::add).single(rs -> rs).ifPresent(rs -> {
            throw new SQLRuntimeException("Procedure has non-empty result set");
        });
        return results.get(0);
    }

    /**
     * Calls this procedure ignoring all its possible results
     *
     * @throws SQLRuntimeException if something went wrong.
     * @see #call(TryFunction, Consumer)
     */
    default void call() {
        call(cs -> null, nil -> {}).single(rs -> null);
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    StoredProcedure skipWarnings(boolean skipWarnings);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    StoredProcedure print(Consumer<String> printer);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    default StoredProcedure print() {
        return print(System.out::println);
    }
}
