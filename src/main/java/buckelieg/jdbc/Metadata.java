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
import javax.annotation.ParametersAreNonnullByDefault;
import java.sql.ResultSet;
import java.sql.SQLType;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * Database ResultSet metadata helper
 * <br/>Typical use-case is to access its methods inside data manipulation handlers
 */
@ParametersAreNonnullByDefault
public interface Metadata {

    /**
     * Retrieves column names that are <code>primary keys</code>
     *
     * @return a {@link List} of column names that are a primary keys or empty if none
     */
    @Nonnull
    default List<String> getPrimaryKeys() {
        return getColumnNames().stream().filter(this::isPrimaryKey).collect(toList());
    }

    /**
     * Retrieves column names that are <code>foreign keys</code>
     *
     * @return a {@link List} of column names that are a foreign keys or empty if none
     */
    @Nonnull
    default List<String> getForeignKeys() {
        return getColumnNames().stream().filter(this::isForeignKey).collect(toList());
    }

    /**
     * Retrieves column names from provided <code>ResultSet</code>
     *
     * @return a {@link List} of column names in this {@link ResultSet}
     */
    @Nonnull
    List<String> getColumnNames();

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
     * @return true if the column is accepts <code>null</code> values, false - otherwise
     * @see java.sql.DatabaseMetaData#attributeNullable
     */
    boolean isNullable(int columnIndex);

    /**
     * Tests if the column with provided name accepts <code>null</code> values
     *
     * @param columnName column name in the result
     * @return true if the column is accepts <code>null</code> values, false - otherwise
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
     * Retrieves a {@link Class} that will be used to represent this column in Java
     *
     * @param columnIndex column index in the result
     * @return a {@link Class} representing this column
     */
    @Nonnull
    Class<?> getType(int columnIndex);

    /**
     * Retrieves a {@link Class} that will be used to represent this column in Java
     *
     * @param columnName column name in the result
     * @return a {@link Class} representing this column
     * @throws NullPointerException if <code>columnName</code> is null
     */
    @Nonnull
    Class<?> getType(String columnName);

    /**
     * Retrieves references table by this foreign key column
     *
     * @param columnIndex column index in the result
     * @return a referenced table's full name (e.g. catalog.schema.name) or null if this column is not a foreign key
     */
    @Nonnull
    Optional<String> referencedTable(int columnIndex);

    /**
     * Retrieves references table by this foreign key column
     *
     * @param columnName column name in the result
     * @return a referenced table's full name (e.g. catalog.schema.name) or null if this column is not a foreign key
     */
    @Nonnull
    Optional<String> referencedTable(String columnName);

}
