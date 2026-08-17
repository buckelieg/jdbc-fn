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

import buckelieg.fn.TryConsumer;
import buckelieg.fn.TrySupplier;

import java.sql.DatabaseMetaData;
import java.sql.JDBCType;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static buckelieg.jdbc.Utils.newSQLRuntimeException;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

final class MetadataImpl implements Metadata {

  private static final String PK_TABLE_CATALOG = "PKTABLE_CAT";
  private static final String PK_TABLE_SCHEMA = "PKTABLE_SCHEM";
  private static final String PK_TABLE_NAME = "PKTABLE_NAME";
  private static final String FK_COLUMN_NAME = "FKCOLUMN_NAME";
  private static final String COLUMN_NAME = "COLUMN_NAME";

  static final class Table {
	private String catalog;
	private String schema;
	private String name;

	@Override
	public String toString() {
	  return format(
			  "%s%s%s",
			  null == catalog || catalog.isEmpty() ? "" : catalog + ".",
			  null == schema || schema.isEmpty() ? "" : schema + ".",
			  name
	  );
	}
  }

  static final class Column {
	private int index;
	private Table ownTable;
	private String name;
	private String label;
	private Boolean pk;
	private Boolean fk;
	private Boolean nullable;
	private SQLType sqlType;
	private Class<?> javaType;
	private Table refTable;

	@Override
	public String toString() {
	  return format("%s:%s%s", index, ownTable == null ? "" : ownTable + ".", name);
	}
  }

  private final Map<String, Column> columnsCache;

  private final TrySupplier<DatabaseMetaData, SQLException> dbMeta;

  private final TrySupplier<ResultSetMetaData, SQLException> rsMeta;

  private final AtomicReference<List<Column>> columns = new AtomicReference<>();

  MetadataImpl(
		  TrySupplier<DatabaseMetaData, SQLException> dbMeta,
		  TrySupplier<ResultSetMetaData, SQLException> rsMeta,
		  Map<String, Column> columnsCache) {
	this.dbMeta = dbMeta;
	this.rsMeta = rsMeta;
	this.columnsCache = columnsCache;
  }

  private List<Column> getColumns() {
	return null == rsMeta ? Collections.emptyList() : columns.updateAndGet(columns -> {
	  if (null == columns) {
		List<Column> buffer = new ArrayList<>();
		try {
		  ResultSetMetaData meta = rsMeta.get();
		  if (null == meta) return Collections.emptyList();
		  for (int columnIndex = 1; columnIndex <= meta.getColumnCount(); columnIndex++)
			buffer.add(createColumn(columnIndex, meta));
		} catch (Exception e) {
		  throw newSQLRuntimeException(e);
		}
		columns = buffer;
	  }
	  return columns;
	});
  }

  MetadataImpl snapshot() {
	getColumns();
	return this;
  }

  MetadataImpl preload() {
	List<Column> resultColumns = getColumns();
	if (null == dbMeta || resultColumns.isEmpty()) return this;

	Map<String, List<Column>> columnsByTable = new HashMap<>();
	for (Column column : resultColumns) {
	  if (null == column.ownTable || null == column.ownTable.name || column.ownTable.name.trim().isEmpty()) {
		cache(column, false, false, null);
		continue;
	  }
	  columnsByTable.computeIfAbsent(column.ownTable.toString(), ignored -> new ArrayList<>()).add(column);
	}

	try {
	  DatabaseMetaData databaseMetaData = dbMeta.get();
	  for (List<Column> tableColumns : columnsByTable.values()) {
		if (!tableColumns.stream().allMatch(this::hasSchemaMetadata)) preload(databaseMetaData, tableColumns);
	  }
	  return this;
	} catch (Exception e) {
	  throw newSQLRuntimeException(e);
	}
  }

  private boolean hasSchemaMetadata(Column column) {
	Column cached = columnsCache.get(column.toString());
	return null != cached && null != cached.pk && null != cached.fk;
  }

  private void preload(DatabaseMetaData databaseMetaData, List<Column> tableColumns) throws SQLException {
	Column sample = tableColumns.get(0);
	Set<String> primaryKeys = new HashSet<>();
	Map<String, Table> referencedTables = new HashMap<>();

	try (java.sql.ResultSet resultSet = databaseMetaData.getPrimaryKeys(
			sample.ownTable.catalog,
			sample.ownTable.schema,
			sample.ownTable.name
	)) {
	  while (resultSet.next()) primaryKeys.add(resultSet.getString(COLUMN_NAME).toUpperCase(Locale.ROOT));
	}

	try (java.sql.ResultSet resultSet = databaseMetaData.getImportedKeys(
			sample.ownTable.catalog,
			sample.ownTable.schema,
			sample.ownTable.name
	)) {
	  while (resultSet.next()) {
		Table referencedTable = new Table();
		referencedTable.catalog = resultSet.getString(PK_TABLE_CATALOG);
		referencedTable.schema = resultSet.getString(PK_TABLE_SCHEMA);
		referencedTable.name = resultSet.getString(PK_TABLE_NAME);
		referencedTables.put(resultSet.getString(FK_COLUMN_NAME).toUpperCase(Locale.ROOT), referencedTable);
	  }
	}

	for (Column column : tableColumns) {
	  String columnName = column.name.toUpperCase(Locale.ROOT);
	  cache(column, primaryKeys.contains(columnName), referencedTables.containsKey(columnName), referencedTables.get(columnName));
	}
  }

  private void cache(Column column, boolean primaryKey, boolean foreignKey, Table referencedTable) {
	columnsCache.compute(column.toString(), (ignored, cached) -> {
	  Column target = null == cached ? column : cached;
	  target.pk = primaryKey;
	  target.fk = foreignKey;
	  target.refTable = referencedTable;
	  return target;
	});
  }

  private Column createColumn(int columnIndex, ResultSetMetaData meta) throws Exception {
	Column column = new Column();
	column.ownTable = new Table();
	column.ownTable.catalog = meta.getCatalogName(columnIndex);
	column.ownTable.schema = meta.getSchemaName(columnIndex);
	column.ownTable.name = meta.getTableName(columnIndex);
	column.name = meta.getColumnName(columnIndex);
	column.nullable = meta.isNullable(columnIndex) == ResultSetMetaData.columnNullable;
	column.sqlType = JDBCType.valueOf(meta.getColumnType(columnIndex));
	column.javaType = Class.forName(meta.getColumnClassName(columnIndex), false, DB.class.getClassLoader());
	column.label = meta.getColumnLabel(columnIndex);
	column.index = columnIndex;
	return column;
  }

  boolean isPrimaryKey(Column column) {
	return enrichColumn(column, c -> {
	  if (null == c.pk) c.pk = false;
	}).pk;
  }

  boolean isForeignKey(Column column) {
	return enrichColumn(column, c -> {
	  if (null == c.fk) c.fk = false;
	}).fk;
  }

  boolean isNullable(Column column) {
	return enrichColumn(column, c -> {
	  if (null == c.nullable) c.nullable = false;
	}).nullable;
  }

  SQLType getSQLType(Column column) {
	return enrichColumn(column, c -> {
	  if (null == c.sqlType) c.sqlType = JDBCType.valueOf(Types.OTHER);
	}).sqlType;
  }

  @Override
  public List<String> names() {
	return getColumns().stream().map(c -> c.name).collect(toList());
  }

  @Override
  public String getName(int columnIndex) {
	return getColumn(columnIndex).name;
  }

  @Override
  public int indexOf(String columnName) {
	if (requireNonNull(columnName, "Column name must be provided").trim().isEmpty())
	  throw new IllegalArgumentException("Column name must not be blank");
	List<Column> columns = getColumns();
	for (int index = 0; index < columns.size(); index++)
	  if (columnName.equalsIgnoreCase(columns.get(index).name)) return index + 1;
	return -1;
  }

  @Override
  public String getLabel(int columnIndex) {
	return getColumn(columnIndex).label;
  }

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
	return isPrimaryKey(getColumn(columnName));
  }

  @Override
  public boolean isForeignKey(int columnIndex) {
	return isForeignKey(getColumn(columnIndex));
  }

  @Override
  public boolean isForeignKey(String columnName) {
	return isForeignKey(getColumn(columnName));
  }

  @Override
  public boolean isNullable(int columnIndex) {
	return isNullable(getColumn(columnIndex));
  }

  @Override
  public boolean isNullable(String columnName) {
	return isNullable(getColumn(columnName));
  }

  @Override
  public SQLType getSQLType(int columnIndex) {
	return getSQLType(getColumn(columnIndex));
  }

  @Override
  public SQLType getSQLType(String columnName) {
	return getSQLType(getColumn(columnName));
  }

  @Override
  public boolean contains(SQLType type) {
	return null != type && getColumns().stream().map(this::getSQLType).anyMatch(sqlType ->
			Objects.equals(sqlType.getVendorTypeNumber(), type.getVendorTypeNumber())
					&& Objects.equals(sqlType.getName(), type.getName())
					&& Objects.equals(sqlType.getVendor(), type.getVendor())
	);
  }

  @Override
  public Class<?> getClass(int columnIndex) {
	return getColumn(columnIndex).javaType;
  }

  @Override
  public Class<?> getClass(String columnName) {
	return getColumn(columnName).javaType;
  }

  @Override
  public Optional<String> getReferencedTable(int columnIndex) {
	return Optional.of(getColumn(columnIndex)).filter(this::isForeignKey).map(c -> c.refTable).map(Objects::toString);
  }

  @Override
  public Optional<String> getReferencedTable(String columnName) {
	return Optional.of(getColumn(columnName)).filter(this::isForeignKey).map(c -> c.refTable).map(Objects::toString);
  }

  @Override
  public void forEachColumn(TryConsumer<Integer, SQLException> action) {
	if (null == action) throw new NullPointerException("Action must be provided");
	try {
	  for (Column column : getColumns()) action.accept(column.index);
	} catch (SQLException e) {
	  throw newSQLRuntimeException(e);
	}
  }


  private Column getColumn(int columnIndex) {
	return getColumns().stream().filter(c -> c.index == columnIndex).findFirst()
			.orElseThrow(() -> new IllegalArgumentException(format("No column exists under provided index %s", columnIndex)));
  }

  private Column getColumn(String columnName) {
	return getColumns().stream().filter(c -> c.name.equalsIgnoreCase(columnName)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException(format("No column exists under provided name %s", columnName)));
  }

  private Column enrichColumn(Column column, TryConsumer<Column, Exception> enricher) {
	return columnsCache.compute(
			column.toString(),
			(k, v) -> {
			  if (null == v) v = column;
			  if (null != enricher) {
				try {
				  enricher.accept(v);
				} catch (Exception e) {
				  throw new RuntimeException(e);
				}
			  }
			  return v;
			}
	);
  }
}
