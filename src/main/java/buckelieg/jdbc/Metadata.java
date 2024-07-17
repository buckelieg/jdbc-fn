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

import buckelieg.jdbc.fn.TryConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLType;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Database {@link ResultSet} metadata helper
 * <br/>Typical use-case is to access its methods inside data manipulation handlers
 */
@ParametersAreNonnullByDefault
public interface Metadata {

  /**
   * Test if provided <code>columnName</code> exists in this {@link ResultSet} object
   *
   * @param columnName a name of the column to test existence with
   * @return true if the column with provided name exists, false - otherwise
   */
  default boolean exists(@Nullable String columnName) {
	return null != columnName && !columnName.trim().isEmpty() && names().stream().anyMatch(col -> col.equalsIgnoreCase(columnName));
  }

  /**
   * Retrieves column names that are <code>primary keys</code>
   *
   * @return a {@link List} of column names that are a primary keys or empty if none
   */
  @Nonnull
  default List<String> primaryKeys() {
	return names().stream().filter(this::isPrimaryKey).collect(toList());
  }

  /**
   * Retrieves column names that are <code>foreign keys</code>
   *
   * @return a {@link List} of column names that are a foreign keys or empty if none
   */
  @Nonnull
  default List<String> foreignKeys() {
	return names().stream().filter(this::isForeignKey).collect(toList());
  }

  /**
   * Retrieves column names from provided <code>ResultSet</code>
   *
   * @return a {@link List} of column names in this {@link ResultSet}
   */
  @Nonnull
  List<String> names();

  /**
   * Retrieves a total number of columns in this {@linkplain ResultSet}
   *
   * @return a column count
   */
  default int columnCount() {
	return names().size();
  }

  /**
   * Retrieves a column name with particular index which starts from {@code 1}
   *
   * @param columnIndex an index of a column which name is being retrieved (starts with 1)
   * @return a name of the column under provided index
   * @throws IllegalArgumentException if {@code columnIndex} is less than 1
   */
  @Nonnull
  String getName(int columnIndex);

  /**
   * Retrieves a column index with name specified or {@code -1} if no column exists with such name
   *
   * @param columnName a name of a column which index is being retrieved
   * @return a column index under provided name or {@code -1} if there is no column with provided name exists
   * @throws NullPointerException     if {@code columnName} is null
   * @throws IllegalArgumentException if {@code columnName} is blank
   */
  int indexOf(String columnName);

  /**
   * Retrieves a column {@code label} by provided index
   *
   * @param columnIndex a column index
   * @return column label
   * @see java.sql.ResultSetMetaData#getColumnLabel(int)
   */
  @Nonnull
  String getLabel(int columnIndex);

  /**
   * Retrieves full column names from provided <code>ResultSet</code>
   *
   * @return a {@link List} of <i>full</i> column names in this {@link ResultSet}
   */
  @Nonnull
  List<String> getColumnFullNames();

  /**
   * Tests if the column under provided index is a primary key
   *
   * @param columnIndex column index in the result
   * @return true if the column is a primary key, false - otherwise
   */
  boolean isPrimaryKey(int columnIndex);

  /**
   * Tests if the column with provided name is a primary key
   *
   * @param columnName column name in the result
   * @return true if the column is a primary key, false - otherwise
   * @throws NullPointerException if <code>columnName</code> is null
   */
  boolean isPrimaryKey(String columnName);

  /**
   * Tests if the column under provided index is a foreign key
   *
   * @param columnIndex column index in the result
   * @return true if the column is a foreign key, false - otherwise
   */
  boolean isForeignKey(int columnIndex);

  /**
   * Tests if the column with provided name is a foreign key
   *
   * @param columnName column name in the result
   * @return true if the column is a foreign key, false - otherwise
   * @throws NullPointerException if <code>columnName</code> is null
   */
  boolean isForeignKey(String columnName);

  /**
   * Tests if the column under provided index accepts <code>null</code> values
   *
   * @param columnIndex column index in the result
   * @return true if the column accepts <code>null</code> values, false - otherwise
   * @see java.sql.DatabaseMetaData#attributeNullable
   */
  boolean isNullable(int columnIndex);

  /**
   * Tests if the column with provided name accepts <code>null</code> values
   *
   * @param columnName column name in the result
   * @return true if the column accepts <code>null</code> values, false - otherwise
   * @throws NullPointerException if <code>columnName</code> is null
   * @see java.sql.DatabaseMetaData#attributeNullable
   */
  boolean isNullable(String columnName);

  /**
   * Retrieves column type as {@link SQLType} object
   *
   * @param columnIndex column index in the result
   * @return an {@link SQLType} of the column
   * @see java.sql.JDBCType
   */
  @Nonnull
  SQLType getSQLType(int columnIndex);

  /**
   * Retrieves column type as {@link SQLType} object
   *
   * @param columnName column name in the result
   * @return an {@link SQLType} of the column
   * @throws NullPointerException if <code>columnName</code> is null
   * @see java.sql.JDBCType
   */
  @Nonnull
  SQLType getSQLType(String columnName);

  /**
   * Tests whether current {@linkplain ResultSet} contains any columns of provided sql type
   *
   * @param type an SQL type to test
   * @return <code>true</code> - if current result set contains a column with provided type, <code>false</code> - otherwise
   * @see SQLType
   * @see JDBCType
   */
  boolean contains(SQLType type);

  /**
   * Tests whether current {@linkplain ResultSet} contains any columns of provided sql type
   *
   * @param sqlType an SQL type to test
   * @return <code>true</code> - if current result set contains a column with provided type, <code>false</code> - otherwise
   * @see java.sql.Types
   * @see JDBCType
   */
  default boolean contains(int sqlType) {
	return contains(JDBCType.valueOf(sqlType));
  }

  /**
   * Tests whether current {@linkplain ResultSet} contains any columns of provided sql type
   *
   * @param types a list of SQL types to test
   * @return <code>true</code> - if current result set contains a column with ny of provided types, <code>false</code> - otherwise
   * @see java.sql.Types
   * @see JDBCType
   */
  default boolean containsAny(SQLType... types) {
	return Stream.of(types).anyMatch(this::contains);
  }

  default boolean containsAny(int... types) {
	return IntStream.of(types).anyMatch(this::contains);
  }

  /**
   * Retrieves a {@link Class} that will be used to represent this column in Java
   *
   * @param columnIndex column index in the result
   * @return a {@link Class} representing this column
   */
  @Nonnull
  Class<?> getClass(int columnIndex);

  /**
   * Retrieves a {@link Class} that will be used to represent this column in Java
   *
   * @param columnName column name in the result
   * @return a {@link Class} representing this column
   * @throws NullPointerException if <code>columnName</code> is null
   */
  @Nonnull
  Class<?> getClass(String columnName);

  /**
   * Retrieves referenced table by this foreign key column index
   *
   * @param columnIndex column index in the result
   * @return a referenced table's full name (e.g. catalog.schema.table_name) if the column under provided index is a foreign key column
   */
  @Nonnull
  Optional<String> getReferencedTable(int columnIndex);

  /**
   * Retrieves referenced table by this foreign key column name
   *
   * @param columnName column name in the result
   * @return a referenced table's full name (e.g. catalog.schema.table_name) if the column under provided name is a foreign key column
   */
  @Nonnull
  Optional<String> getReferencedTable(@Nullable String columnName);

  /**
   * Performs provided {@code action} on each column index in this {@linkplain ResultSet} object
   *
   * @param action an action to perform
   * @throws NullPointerException if {@code action} is null
   */
  void forEachColumn(TryConsumer<Integer, SQLException> action);

}
