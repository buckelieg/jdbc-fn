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
import buckelieg.jdbc.fn.TrySupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.ParametersAreNullableByDefault;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static buckelieg.jdbc.Utils.listResultSet;
import static buckelieg.jdbc.Utils.newSQLRuntimeException;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@ParametersAreNonnullByDefault
final class RSMeta implements Metadata {

  private static final String PK_TABLE_CATALOG = "PKTABLE_CAT";
  private static final String PK_TABLE_SCHEMA = "PKTABLE_SCHEM";
  private static final String PK_TABLE_NAME = "PKTABLE_NAME";
  private static final String PK_COLUMN_NAME = "PKCOLUMN_NAME";
  private static final String FK_COLUMN_NAME = "FKCOLUMN_NAME";
  private static final String NULLABLE = "NULLABLE";
  private static final String DATA_TYPE = "DATA_TYPE";
  private static final String COLUMN_NAME = "COLUMN_NAME";

  static final class Table {
	private String catalog;
	private String schema;
	private String name;

	@Override
	public String toString() {
	  return format(
			  "%s%s%s",
			  catalog == null || catalog.isEmpty() ? "" : catalog + ".",
			  schema == null || schema.isEmpty() ? "" : schema + ".",
			  name
	  );
	}
  }

  static final class Column {
	private int index;
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
	  return format(
			  "%s:%s%s%s%s",
			  index,
			  ownTable.catalog == null || ownTable.catalog.isEmpty() ? "" : ownTable.catalog + ".",
			  ownTable.schema == null || ownTable.schema.isEmpty() ? "" : ownTable.schema + ".",
			  ownTable.name == null || ownTable.name.isEmpty() ? "" : ownTable.name + ".",
			  name
	  );
	}
  }

  private final Map<String, Column> columnsCache;

  private final TrySupplier<DatabaseMetaData, SQLException> dbMeta;

  private final TrySupplier<ResultSetMetaData, SQLException> rsMeta;

  private final AtomicReference<List<Column>> columns = new AtomicReference<>();

  private final Map<String, String> columnLabels = new ConcurrentHashMap<>();

  RSMeta(TrySupplier<DatabaseMetaData, SQLException> dbMeta, TrySupplier<ResultSetMetaData, SQLException> rsMeta, Map<String, Column> columnsCache) {
	this.dbMeta = dbMeta;
	this.rsMeta = rsMeta;
	this.columnsCache = columnsCache;
  }

  private List<Column> getColumns() {
	return columns.updateAndGet(columns -> {
	  if (null == columns) {
		List<Column> buffer = new ArrayList<>();
		try {
		  for (int col = 1; col <= rsMeta.get().getColumnCount(); col++) {
			buffer.add(getColumn(col));
		  }
		  columns = buffer;
		} catch (SQLException e) {
		  throw newSQLRuntimeException(e);
		}
	  }
	  return columns;
	});
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
  public List<String> names() {
	return getColumns().stream().map(c -> c.name).collect(toList());
  }

  @Nonnull
  @Override
  public String getName(int columnIndex) {
	if (columnIndex < 1) throw new IllegalArgumentException("Column index must start at 1");
	List<Column> columns = getColumns();
	if (columnIndex > columns.size())
	  throw new IllegalArgumentException(format("No column exists under provided index %s", columnIndex));
	return columns.get(columnIndex - 1).name;
  }

  @Override
  public int indexOf(String columnName) {
	if (requireNonNull(columnName, "Column name must be provided").trim().isEmpty())
	  throw new IllegalArgumentException("Column name must not be blank");
	List<Column> columns = getColumns();
	for (int index = 0; index < columns.size(); index++) {
	  if (columnName.equalsIgnoreCase(columns.get(index).name)) return index + 1;
	}
	return -1;
  }

  @Nonnull
  @Override
  public String getLabel(int columnIndex) {
	return columnLabels.computeIfAbsent(getName(columnIndex), name -> {
	  try {
		return rsMeta.get().getColumnLabel(columnIndex);
	  } catch (SQLException e) {
		throw newSQLRuntimeException(e);
	  }
	});
  }

  @Nonnull
  @Override
  public List<String> getColumnFullNames() {
	return getColumns().stream().map(Column::toString).collect(toList());
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

  @Override
  public boolean contains(SQLType type) {
	return null != type && getColumns().stream().map(this::getSQLType).anyMatch(sqlType ->
			Objects.equals(sqlType.getVendorTypeNumber(), type.getVendorTypeNumber())
					&& Objects.equals(sqlType.getName(), type.getName())
					&& Objects.equals(sqlType.getVendor(), type.getVendor())
	);
  }

  @Nonnull
  @Override
  public Class<?> getClass(int columnIndex) {
	return getColumn(columnIndex).javaType;
  }

  @Nonnull
  @Override
  public Class<?> getClass(String columnName) {
	Optional<Class<?>> aClass = getColumn(columnName).map(c -> c.javaType);
	return aClass.isPresent() ? aClass.get() : Object.class;
  }

  @Nonnull
  @Override
  public Optional<String> getReferencedTable(int columnIndex) {
	return ofNullable(!isForeignKey(columnIndex) ? null : getColumn(columnIndex).refTable.toString());
  }

  @Nonnull
  @Override
  public Optional<String> getReferencedTable(@Nullable String columnName) {
	return getColumn(columnName).filter(this::isForeignKey).map(c -> c.refTable.toString());
  }

  @Override
  public void forEachColumn(TryConsumer<Integer, SQLException> action) {
	if (null == action) throw new NullPointerException("Action must be provided");
	try {
	  for (Column column : getColumns()) {
		action.accept(column.index);
	  }
	} catch (SQLException e) {
	  throw newSQLRuntimeException(e);
	}
  }

  private boolean isPrimaryKey(Table table, String column) {
	return getColumn(table.catalog, table.schema, table.name, column, c -> {
	  if (c.pk == null)
		c.pk = listResultSet(dbMeta.get().getPrimaryKeys(table.catalog, table.schema, table.name), rs -> rs.getString(COLUMN_NAME)).stream().anyMatch(name -> name.equalsIgnoreCase(column));
	}).pk;
  }

  private boolean isForeignKey(Table table, String column) {
	return getColumn(table.catalog, table.schema, table.name, column, c -> {
	  if (c.fk == null) {
		c.fk = listResultSet(
				dbMeta.get().getImportedKeys(table.catalog, table.schema, table.name),
				rs -> {
				  Map<String, String> map = new HashMap<>();
				  map.put(PK_TABLE_CATALOG, rs.getString(PK_TABLE_CATALOG));
				  map.put(PK_TABLE_SCHEMA, rs.getString(PK_TABLE_SCHEMA));
				  map.put(PK_TABLE_NAME, rs.getString(PK_TABLE_NAME));
				  map.put(PK_COLUMN_NAME, rs.getString(PK_COLUMN_NAME));
				  map.put(FK_COLUMN_NAME, rs.getString(FK_COLUMN_NAME));
				  return map;
				})
				.stream()
				.filter(map -> map.getOrDefault(FK_COLUMN_NAME, "").equalsIgnoreCase(column))
				.findFirst()
				.map(map -> {
				  c.fk = true;
				  c.refTable = new Table();
				  c.refTable.catalog = map.get(PK_TABLE_CATALOG);
				  c.refTable.schema = map.get(PK_TABLE_SCHEMA);
				  c.refTable.name = map.get(PK_TABLE_NAME);
				  return c.fk;
				})
				.orElse(false);
	  }
	}).fk;
  }

  private boolean isNullable(Table table, String column) {
	return isNullable(table.catalog, table.schema, table.name, column);
  }

  private boolean isNullable(String catalog, String schema, String table, String column) {
	return getColumn(catalog, schema, table, column, c -> {
	  if (c.nullable == null)
		c.nullable = listResultSet(dbMeta.get().getColumns(catalog, schema, table, column), rs -> rs.getInt(NULLABLE)).stream().anyMatch(mode -> mode == DatabaseMetaData.columnNullable);
	}).nullable;
  }

  private SQLType getSQLType(Table table, String column) {
	return getSQLType(table.catalog, table.schema, table.name, column);
  }

  private SQLType getSQLType(String catalog, String schema, String table, String column) {
	return getColumn(catalog, schema, table, column, c -> {
	  if (c.sqlType == null)
		c.sqlType = JDBCType.valueOf(listResultSet(dbMeta.get().getColumns(catalog, schema, table, column), rs -> rs.getInt(DATA_TYPE)).stream().findFirst().orElse(Types.OTHER));
	}).sqlType;
  }

  private Column getColumn(int columnIndex, @Nullable TryConsumer<Column, Exception> enricher) {
	try {
	  ResultSetMetaData meta = rsMeta.get();
	  return getColumn(
			  meta.getCatalogName(columnIndex),
			  meta.getSchemaName(columnIndex),
			  meta.getTableName(columnIndex),
			  meta.getColumnName(columnIndex),
			  c -> {
				c.index = columnIndex;
				if (c.nullable == null) c.nullable = meta.isNullable(columnIndex) == ResultSetMetaData.columnNullable;
				if (c.sqlType == null) c.sqlType = JDBCType.valueOf(meta.getColumnType(columnIndex));
				if (c.javaType == null)
				  c.javaType = Class.forName(meta.getColumnClassName(columnIndex), false, ClassLoader.getSystemClassLoader());
				getEnricher(enricher).accept(c);
			  }
	  );
	} catch (SQLException e) {
	  throw newSQLRuntimeException(e);
	}
  }

  private Column getColumn(int columnIndex) {
	try {
	  ResultSetMetaData meta = rsMeta.get();
	  return getColumn(
			  meta.getCatalogName(columnIndex),
			  meta.getSchemaName(columnIndex),
			  meta.getTableName(columnIndex),
			  meta.getColumnName(columnIndex),
			  col -> col.index = columnIndex
	  );
	} catch (SQLException e) {
	  throw newSQLRuntimeException(e);
	}
  }

  private Optional<Column> getColumn(@Nullable String columnName) {
	return null == columnName || columnName.trim().isEmpty() ? Optional.empty() : getColumns().stream().filter(c -> c.name.equalsIgnoreCase(columnName)).findFirst();
  }

  private Column getColumn(Table table, String column, @Nullable TryConsumer<Column, Exception> enricher) {
	return getColumn(table.catalog, table.schema, table.name, column, enricher);
  }

  @ParametersAreNullableByDefault
  private Column getColumn(String catalog, String schema, String table, String column, TryConsumer<Column, Exception> enricher) {
	boolean hasCatalog = catalog == null || catalog.trim().isEmpty();
	boolean hasSchema = schema == null || schema.trim().isEmpty();
	boolean hasTable = table == null || table.trim().isEmpty();
	boolean hasColumn = column == null || column.trim().isEmpty();
	return columnsCache.compute(
			format(
					"%s%s%s%s",
					hasCatalog ? "" : catalog,
					hasSchema ? "" : (hasCatalog ? "." : "") + schema,
					hasTable ? "" : "." + table,
					hasColumn ? "" : "." + column
			),
			(k, v) -> {
			  if (v == null) {
				v = new Column();
				v.ownTable = new Table();
				v.ownTable.catalog = catalog;
				v.ownTable.schema = schema;
				v.ownTable.name = table;
				v.name = column;
			  }
			  getEnricher(enricher).accept(v);
			  return v;
			}
	);
  }

  private Consumer<Column> getEnricher(@Nullable TryConsumer<Column, Exception> enricher) {
	return c -> {
	  if (enricher != null) {
		try {
		  enricher.accept(c);
		} catch (Exception e) {
		  throw new RuntimeException(e);
		}
	  }
	};
  }
}
