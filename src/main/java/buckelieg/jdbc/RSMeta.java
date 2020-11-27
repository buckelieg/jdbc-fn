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

import buckelieg.jdbc.fn.TryConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import static buckelieg.jdbc.Utils.newSQLRuntimeException;
import static buckelieg.jdbc.Utils.rsStream;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@ParametersAreNonnullByDefault
final class RSMeta implements Metadata {

    static final class Table {
        private String catalog;
        private String schema;
        private String name;

        @Override
        public String toString() {
            return format("%s%s%s",
                    catalog == null || catalog.isEmpty() ? "" : catalog + ".",
                    schema == null || schema.isEmpty() ? "" : schema + ".",
                    name
            );
        }
    }

    static final class Column {
        private Table ownTable;
        private String name;
        private Boolean pk;
        private Boolean fk;
        private Boolean nullable;
        private SQLType sqlType;
        private Class<?> javaType;
        private Table refTable;

        @Override
        public String toString() {
            return format("%s%s%s%s",
                    ownTable.catalog == null || ownTable.catalog.isEmpty() ? "" : ownTable.catalog + ".",
                    ownTable.schema == null || ownTable.schema.isEmpty() ? "" : ownTable.schema + ".",
                    ownTable.name == null || ownTable.name.isEmpty() ? "" : ownTable.name + ".",
                    name
            );
        }
    }

    private final ConcurrentMap<String, Column> columnsCache;
    private final DatabaseMetaData dbMeta;
    private final ResultSetMetaData rsMeta;

    public RSMeta(Connection connection, ResultSet resultSet, ConcurrentMap<String, Column> columnsCache) {
        try {
            this.dbMeta = connection.getMetaData();
            this.rsMeta = resultSet.getMetaData();
            this.columnsCache = columnsCache;
        } catch (SQLException e) {
            throw newSQLRuntimeException(e);
        }
    }

    private List<Column> getColumns() {
        List<Column> columns = new ArrayList<>();
        try {
            for (int col = 1; col <= rsMeta.getColumnCount(); col++) {
                columns.add(getColumn(col));
            }
        } catch (SQLException e) {
            throw newSQLRuntimeException(e);
        }
        return columns;
    }

    String getCatalog(Column c) {
        return c.ownTable.catalog;
    }

    String getSchema(Column c) {
        return c.ownTable.schema;
    }

    String getTable(Column c) {
        return c.ownTable.name;
    }

    String getName(Column c) {
        return c.name;
    }

    boolean isPrimaryKey(Column c) {
        return isPrimaryKey(c.ownTable, c.name);
    }

    boolean isForeignKey(Column c) {
        return isForeignKey(c.ownTable, c.name);
    }

    boolean isNullable(Column c) {
        return isNullable(c.ownTable, c.name);
    }

    SQLType getSQLType(Column c) {
        return getSQLType(c.ownTable, c.name);
    }

    @Override
    public boolean exists(@Nullable String columnName) {
        return getColumn(columnName).isPresent();
    }

    @Nonnull
    @Override
    public List<String> getColumnNames() {
        return getColumns().stream().map(c -> c.name).collect(toList());
    }

    @Override
    public boolean isPrimaryKey(int columnIndex) {
        return isPrimaryKey(getColumn(columnIndex));
    }

    @Override
    public boolean isPrimaryKey(String columnName) {
        return getColumn(requireNonNull(columnName, "Column name must be provided")).map(this::isPrimaryKey).orElse(false);
    }

    @Override
    public boolean isForeignKey(int columnIndex) {
        return isForeignKey(getColumn(columnIndex));
    }

    @Override
    public boolean isForeignKey(String columnName) {
        return getColumn(requireNonNull(columnName, "Column name must be provided")).map(this::isForeignKey).orElse(false);
    }

    @Override
    public boolean isNullable(int columnIndex) {
        return isNullable(getColumn(columnIndex));
    }

    @Override
    public boolean isNullable(String columnName) {
        return getColumn(requireNonNull(columnName, "Column name must be provided")).map(this::isNullable).orElse(false);
    }

    @Nonnull
    @Override
    public SQLType getSQLType(int columnIndex) {
        return getSQLType(getColumn(columnIndex));
    }

    @Nonnull
    @Override
    public SQLType getSQLType(String columnName) {
        return getColumn(requireNonNull(columnName, "Column name must be provided")).map(this::getSQLType).orElse(JDBCType.OTHER);
    }

    @Nonnull
    @Override
    public Class<?> getType(int columnIndex) {
        return getColumn(columnIndex).javaType;
    }

    @Nonnull
    @Override
    public Class<?> getType(String columnName) {
        Optional<Class<?>> aClass = getColumn(requireNonNull(columnName, "Column name must be provided")).map(c -> c.javaType);
        return aClass.isPresent() ? aClass.get() : Object.class;
    }

    @Nonnull
    @Override
    public Optional<String> referencedTable(int columnIndex) {
        Column c = getColumn(columnIndex);
        return ofNullable(!isForeignKey(columnIndex) ? null : c.refTable.toString());
    }

    @Nonnull
    @Override
    public Optional<String> referencedTable(@Nullable String columnName) {
        return getColumn(columnName).filter(this::isForeignKey).map(c -> c.refTable.toString());
    }

    private boolean isPrimaryKey(Table table, String column) {
        return getColumn(table.catalog, table.schema, table.name, column, c -> {
            if (c.pk == null) {
                c.pk = rsStream(dbMeta.getPrimaryKeys(table.catalog, table.schema, table.name), rs -> rs.getString("COLUMN_NAME")).anyMatch(name -> name.equalsIgnoreCase(column));
            }
        }).pk;
    }

    private boolean isForeignKey(Table table, String column) {
        return getColumn(table.catalog, table.schema, table.name, column, c -> {
            if (c.fk == null) {
                c.fk = rsStream(dbMeta.getImportedKeys(table.catalog, table.schema, table.name), rs -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("PKTABLE_CAT", rs.getString("PKTABLE_CAT"));
                    map.put("PKTABLE_SCHEM", rs.getString("PKTABLE_SCHEM"));
                    map.put("PKTABLE_NAME", rs.getString("PKTABLE_NAME"));
                    map.put("PKCOLUMN_NAME", rs.getString("PKCOLUMN_NAME"));
                    map.put("FKCOLUMN_NAME", rs.getString("FKCOLUMN_NAME"));
                    return map;
                })
                        .filter(map -> map.getOrDefault("FKCOLUMN_NAME", "").equalsIgnoreCase(column))
                        .findFirst()
                        .map(map -> {
                            c.fk = true;
                            c.refTable = new Table();
                            c.refTable.catalog = map.get("PKTABLE_CAT");
                            c.refTable.schema = map.get("PKTABLE_SCHEM");
                            c.refTable.name = map.get("PKTABLE_NAME");
                            return c.fk;
                        }).orElse(false);
            }
        }).fk;
    }

    private boolean isNullable(Table table, String column) {
        return isNullable(table.catalog, table.schema, table.name, column);
    }

    private boolean isNullable(String catalog, String schema, String table, String column) {
        return getColumn(catalog, schema, table, column, c -> {
            if (c.nullable == null) {
                c.nullable = rsStream(dbMeta.getColumns(catalog, schema, table, column), rs -> rs.getInt("NULLABLE")).anyMatch(mode -> mode == DatabaseMetaData.columnNullable);
            }
        }).nullable;
    }

    private SQLType getSQLType(Table table, String column) {
        return getSQLType(table.catalog, table.schema, table.name, column);
    }

    private SQLType getSQLType(String catalog, String schema, String table, String column) {
        return getColumn(catalog, schema, table, column, c -> {
            if (c.sqlType == null) {
                c.sqlType = JDBCType.valueOf(rsStream(dbMeta.getColumns(catalog, schema, table, column), rs -> rs.getInt("DATA_TYPE")).findFirst().orElse(Types.OTHER));
            }
        }).sqlType;
    }

    private Column getColumn(int columnIndex, @Nullable TryConsumer<Column, Exception> enricher) {
        try {
            return getColumn(rsMeta.getCatalogName(columnIndex), rsMeta.getSchemaName(columnIndex), rsMeta.getTableName(columnIndex), rsMeta.getColumnName(columnIndex), c -> {
                if (c.nullable == null) {
                    c.nullable = rsMeta.isNullable(columnIndex) == ResultSetMetaData.columnNullable;
                }
                if (c.sqlType == null) {
                    c.sqlType = JDBCType.valueOf(rsMeta.getColumnType(columnIndex));
                }
                if (c.javaType == null) {
                    c.javaType = Class.forName(rsMeta.getColumnClassName(columnIndex), false, ClassLoader.getSystemClassLoader());
                }
                fromEnricher(enricher).accept(c);
            });
        } catch (SQLException e) {
            throw newSQLRuntimeException(e);
        }
    }

    private Column getColumn(int columnIndex) {
        try {
            return getColumn(rsMeta.getCatalogName(columnIndex), rsMeta.getSchemaName(columnIndex), rsMeta.getTableName(columnIndex), rsMeta.getColumnName(columnIndex), c -> {
                if (c.nullable == null) {
                    c.nullable = rsMeta.isNullable(columnIndex) == ResultSetMetaData.columnNullable;
                }
                if (c.sqlType == null) {
                    c.sqlType = JDBCType.valueOf(rsMeta.getColumnType(columnIndex));
                }
                if (c.javaType == null) {
                    c.javaType = Class.forName(rsMeta.getColumnClassName(columnIndex), false, ClassLoader.getSystemClassLoader());
                }
            });
        } catch (SQLException e) {
            throw newSQLRuntimeException(e);
        }
    }

    private Optional<Column> getColumn(@Nullable String columnName) {
        return null == columnName || columnName.isEmpty() ? Optional.empty() : getColumns().stream().filter(c -> c.name.equalsIgnoreCase(columnName)).findFirst();
    }

    private Column getColumn(Table table, String column, @Nullable TryConsumer<Column, Exception> enricher) {
        return getColumn(table.catalog, table.schema, table.name, column, enricher);
    }

    private Column getColumn(String catalog, String schema, String table, String column, @Nullable TryConsumer<Column, Exception> enricher) {
        return columnsCache.compute(
                format("%s.%s.%s.%s", catalog, schema, table, column),
                (k, v) -> {
                    if (v == null) {
                        v = new Column();
                        v.ownTable = new Table();
                        v.ownTable.catalog = catalog;
                        v.ownTable.schema = schema;
                        v.ownTable.name = table;
                        v.name = column;
                    }
                    fromEnricher(enricher).accept(v);
                    return v;
                }
        );
    }

    private Consumer<Column> fromEnricher(@Nullable TryConsumer<Column, Exception> enricher) {
        return c -> {
            if (enricher != null) {
                try {
                    enricher.accept(c);
                } catch (SQLException e) {
                    throw newSQLRuntimeException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

}
