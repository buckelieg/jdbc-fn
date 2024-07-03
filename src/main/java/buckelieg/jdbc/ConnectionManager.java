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
package buckelieg.jdbc;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * A manager that is responsible to create and destroy a connections to the database<br/>
 */
public interface ConnectionManager extends AutoCloseable {

  /**
   * Provides a {@linkplain Connection} instance. Either new one or from the pool (implementation-specific behaviour)
   *
   * @return a {@linkplain Connection} instance
   * @throws SQLException in case of any error
   */
  @Nonnull
  Connection getConnection() throws SQLException;

  /**
   * Attempts to close provided connection
   *
   * @param connection a connection to close
   * @throws SQLException is case of any error
   */
  void close(@Nullable Connection connection) throws SQLException;

  /**
   * Shuts down all possessed connections and clears all necessary resources
   *
   * @throws SQLException in case of any errors
   */
  @Override
  void close() throws SQLException;

}
