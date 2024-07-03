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

import buckelieg.jdbc.fn.TryQuadConsumer;
import buckelieg.jdbc.fn.TryTriConsumer;

import javax.annotation.Nonnull;
import javax.sql.RowSet;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;

import static java.util.Objects.requireNonNull;

final class ValueSetters implements ValueWriter {

  private final ResultSet resultSet;
  private final RowSet rowSet;
  private final PreparedStatement preparedStatement;
  private final CallableStatement callableStatement;

  private final Metadata metadata;

  private ValueSetters(Metadata metadata, ResultSet resultSet, PreparedStatement preparedStatement) {
	this.metadata = metadata;
	if (resultSet instanceof RowSet) {
	  this.rowSet = (RowSet) resultSet;
	  this.resultSet = null;
	  this.preparedStatement = null;
	  this.callableStatement = null;
	} else if (resultSet instanceof ResultSet) {
	  this.resultSet = resultSet;
	  this.rowSet = null;
	  this.preparedStatement = null;
	  this.callableStatement = null;
	} else if (preparedStatement instanceof CallableStatement) {
	  this.callableStatement = (CallableStatement) preparedStatement;
	  this.preparedStatement = null;
	  this.resultSet = null;
	  this.rowSet = null;
	} else {
	  this.preparedStatement = preparedStatement;
	  this.callableStatement = null;
	  this.resultSet = null;
	  this.rowSet = null;
	}
  }

  static <T extends ResultSet> ValueWriter writer(Metadata metadata, T resultSet) {
	return new ValueSetters(metadata, requireNonNull(resultSet, "ResultSet instance must be provided"), null);
  }

  static <T extends PreparedStatement> ValueWriter writer(Metadata metadata, T statement) {
	return new ValueSetters(metadata, null, requireNonNull(statement, "Statement instance must be provided"));
  }

  @SuppressWarnings("unchecked")
  private <T, S extends PreparedStatement, R extends RowSet> void setValue(
		  int index, T value,
		  TryTriConsumer<S, Integer, T, SQLException> statementSetter,
		  TryTriConsumer<ResultSet, Integer, T, SQLException> resultSetSetter,
		  TryTriConsumer<R, Integer, T, SQLException> rowSetSetter
  ) throws SQLException {
	if (null != preparedStatement) statementSetter.accept((S) preparedStatement, index, value);
	else if (null != callableStatement) ((TryTriConsumer<CallableStatement, Integer, T, SQLException>) statementSetter).accept(callableStatement, index, value);
	else if (null != resultSet) resultSetSetter.accept(resultSet, index, value);
	else if (null != rowSet) rowSetSetter.accept((R) rowSet, index, value);
  }

  private <T> void setValue(
		  String name, T value,
		  TryTriConsumer<PreparedStatement, Integer, T, SQLException> preparedStatementSetter,
		  TryTriConsumer<CallableStatement, String, T, SQLException> callableStatementSetter,
		  TryTriConsumer<ResultSet, String, T, SQLException> resultSetSetter,
		  TryTriConsumer<RowSet, String, T, SQLException> rowSetSetter
  ) throws SQLException {
	if (null != preparedStatement) preparedStatementSetter.accept(preparedStatement, indexOf(name), value);
	else if (null != callableStatement) callableStatementSetter.accept(callableStatement, name, value);
	else if (null != resultSet) resultSetSetter.accept(resultSet, name, value);
	else if (null != rowSet) rowSetSetter.accept(rowSet, name, value);
  }

  private int indexOf(String name) throws SQLException {
	return metadata.indexOf(name);
  }

  @SuppressWarnings("unchecked")
  private <T, S extends PreparedStatement, N> void setValue(
		  int index, T value1, N value2,
		  TryQuadConsumer<S, Integer, T, N, SQLException> statementSetter,
		  TryQuadConsumer<ResultSet, Integer, T, N, SQLException> resultSetSetter,
		  TryQuadConsumer<RowSet, Integer, T, N, SQLException> rowSetSetter
  ) throws SQLException {
	if (null != preparedStatement) statementSetter.accept((S) preparedStatement, index, value1, value2);
	else if (null != callableStatement) ((TryQuadConsumer<CallableStatement, Integer, T, N, SQLException>) statementSetter).accept(callableStatement, index, value1, value2);
	else if (null != resultSet) resultSetSetter.accept(resultSet, index, value1, value2);
	else if (null != rowSet) rowSetSetter.accept(rowSet, index, value1, value2);
  }

  private <T, N> void setValue(
		  String name, T value1, N value2,
		  TryQuadConsumer<PreparedStatement, Integer, T, N, SQLException> preparedStatementSetter,
		  TryQuadConsumer<CallableStatement, String, T, N, SQLException> callableStatementSetter,
		  TryQuadConsumer<ResultSet, String, T, N, SQLException> resultSetSetter,
		  TryQuadConsumer<RowSet, String, T, N, SQLException> rowSetSetter
  ) throws SQLException {
	if (null != preparedStatement) {
	  ResultSetMetaData meta = preparedStatement.getMetaData();
	  for (int index = 1; index <= meta.getColumnCount(); index++) {
		if (name.equalsIgnoreCase(meta.getColumnName(index))) {
		  preparedStatementSetter.accept(preparedStatement, index, value1, value2);
		  return;
		}
	  }
	} else if (null != callableStatement) callableStatementSetter.accept(callableStatement, name, value1, value2);
	else if (null != resultSet) resultSetSetter.accept(resultSet, name, value1, value2);
	else if (null != rowSet) rowSetSetter.accept(rowSet, name, value1, value2);
  }

  @Nonnull
  @Override
  public Metadata meta() {
	return metadata;
  }

  @Override
  public void setNull(int index, int sqlType) throws SQLException {
	setNull(index, sqlType, null);
  }

  @Override
  public void setBoolean(int index, boolean value) throws SQLException {
	setValue(index, value, PreparedStatement::setBoolean, ResultSet::updateBoolean, RowSet::setBoolean);
  }

  @Override
  public void setByte(int index, byte value) throws SQLException {
	setValue(index, value, PreparedStatement::setByte, ResultSet::updateByte, RowSet::setByte);
  }

  @Override
  public void setShort(int index, short value) throws SQLException {
	setValue(index, value, PreparedStatement::setShort, ResultSet::updateShort, RowSet::setShort);
  }

  @Override
  public void setInt(int index, int value) throws SQLException {
	setValue(index, value, PreparedStatement::setInt, ResultSet::updateInt, RowSet::setInt);
  }

  @Override
  public void setLong(int index, long value) throws SQLException {
	setValue(index, value, PreparedStatement::setLong, ResultSet::updateLong, RowSet::setLong);
  }

  @Override
  public void setFloat(int index, float value) throws SQLException {
	setValue(index, value, PreparedStatement::setFloat, ResultSet::updateFloat, RowSet::setFloat);
  }

  @Override
  public void setDouble(int index, double value) throws SQLException {
	setValue(index, value, PreparedStatement::setDouble, ResultSet::updateDouble, RowSet::setDouble);
  }

  @Override
  public void setBigDecimal(int index, BigDecimal value) throws SQLException {
	setValue(index, value, PreparedStatement::setBigDecimal, ResultSet::updateBigDecimal, RowSet::setBigDecimal);
  }

  @Override
  public void setString(int index, String value) throws SQLException {
	setValue(index, value, PreparedStatement::setString, ResultSet::updateString, RowSet::setString);
  }

  @Override
  public void setBytes(int index, byte[] value) throws SQLException {
	setValue(index, value, PreparedStatement::setBytes, ResultSet::updateBytes, RowSet::setBytes);
  }

  @Override
  public void setDate(int index, Date value) throws SQLException {
	setValue(index, value, PreparedStatement::setDate, ResultSet::updateDate, RowSet::setDate);
  }

  @Override
  public void setTime(int index, Time value) throws SQLException {
	setValue(index, value, PreparedStatement::setTime, ResultSet::updateTime, RowSet::setTime);
  }

  @Override
  public void setTimestamp(int index, Timestamp value) throws SQLException {
	setValue(index, value, PreparedStatement::setTimestamp, ResultSet::updateTimestamp, RowSet::setTimestamp);
  }

  @Override
  public void setAsciiStream(int index, InputStream value, int length) throws SQLException {
	setValue(index, value, length, PreparedStatement::setAsciiStream, ResultSet::updateAsciiStream, RowSet::setAsciiStream);
  }

  @SuppressWarnings("deprecation")
  @Override
  public void setUnicodeStream(int index, InputStream value, int length) throws SQLException {
	setValue(
			index, value, length,
			PreparedStatement::setUnicodeStream,
			(rs, i, val, size) -> rs.updateCharacterStream(i, new InputStreamReader(val), size),
			(rs, i, val, size) -> rs.setCharacterStream(i, new InputStreamReader(val), size)
	);
  }

  @Override
  public void setBinaryStream(int index, InputStream value, int length) throws SQLException {
	setValue(index, value, length, PreparedStatement::setBinaryStream, ResultSet::updateBinaryStream, RowSet::setBinaryStream);
  }

  @Override
  public void setObject(int index, Object value, int targetSqlType) throws SQLException {
	setValue(index, value, targetSqlType, PreparedStatement::setObject, ResultSet::updateObject, RowSet::setObject);
  }

  @Override
  public void setObject(int index, Object value) throws SQLException {
	setValue(index, value, PreparedStatement::setObject, ResultSet::updateObject, RowSet::setObject);
  }

  @Override
  public void setCharacterStream(int index, Reader value, int length) throws SQLException {
	setValue(index, value, length, PreparedStatement::setCharacterStream, ResultSet::updateCharacterStream, RowSet::setNCharacterStream);
  }

  @Override
  public void setRef(int index, Ref value) throws SQLException {
	setValue(index, value, PreparedStatement::setRef, ResultSet::updateRef, RowSet::setRef);
  }

  @Override
  public void setBlob(int index, Blob value) throws SQLException {
	setValue(index, value, PreparedStatement::setBlob, ResultSet::updateBlob, RowSet::setBlob);
  }

  @Override
  public void setClob(int index, Clob value) throws SQLException {
	setValue(index, value, PreparedStatement::setClob, ResultSet::updateClob, RowSet::setClob);

  }

  @Override
  public void setArray(int index, Array value) throws SQLException {
	setValue(index, value, PreparedStatement::setArray, ResultSet::updateArray, RowSet::setArray);

  }

  @Override
  public void setDate(int index, Date value, Calendar cal) throws SQLException {
	setValue(index, value, cal, PreparedStatement::setDate, (rs, i, v, c) -> rs.updateDate(i, v), RowSet::setDate);
  }

  @Override
  public void setTime(int index, Time value, Calendar cal) throws SQLException {
	setValue(index, value, cal, PreparedStatement::setTime, (rs, i, v, c) -> rs.updateTime(i, v), RowSet::setTime);
  }

  @Override
  public void setTimestamp(int index, Timestamp value, Calendar cal) throws SQLException {
	setValue(index, value, cal, PreparedStatement::setTimestamp, (rs, i, v, c) -> rs.updateTimestamp(i, v), RowSet::setTimestamp);
  }

  @Override
  public void setNull(int index, int sqlType, String typeName) throws SQLException {
	if (null != preparedStatement) {
	  if (null == typeName) preparedStatement.setNull(index, sqlType);
	  else preparedStatement.setNull(index, sqlType, typeName);
	} else if (null != callableStatement) {
	  if (null == typeName) callableStatement.setNull(index, sqlType);
	  else callableStatement.setNull(index, sqlType, typeName);
	} else if (null != resultSet) {
	  resultSet.updateNull(index);
	} else if (null != rowSet) {
	  if (null == typeName) rowSet.setNull(index, sqlType);
	  else rowSet.setNull(index, sqlType, typeName);
	}
  }

  @Override
  public void setURL(int index, URL value) throws SQLException {
	setValue(index, value, PreparedStatement::setURL, ResultSet::updateObject, RowSet::setURL);

  }

  @Override
  public void setRowId(int index, RowId value) throws SQLException {
	setValue(index, value, PreparedStatement::setRowId, ResultSet::updateRowId, RowSet::setRowId);
  }

  @Override
  public void setNString(int index, String value) throws SQLException {
	setValue(index, value, PreparedStatement::setNString, ResultSet::updateNString, RowSet::setNString);
  }

  @Override
  public void setNCharacterStream(int index, Reader value, long length) throws SQLException {
	setValue(index, value, length, PreparedStatement::setNCharacterStream, ResultSet::updateNCharacterStream, RowSet::setNCharacterStream);
  }

  @Override
  public void setNClob(int index, NClob value) throws SQLException {
	setValue(index, value, PreparedStatement::setNClob, ResultSet::updateNClob, RowSet::setNClob);
  }

  @Override
  public void setClob(int index, Reader value, long length) throws SQLException {
	setValue(index, value, length, PreparedStatement::setClob, ResultSet::updateClob, RowSet::setClob);
  }

  @Override
  public void setBlob(int index, InputStream value, long length) throws SQLException {
	setValue(index, value, length, PreparedStatement::setBlob, ResultSet::updateBlob, RowSet::setBlob);
  }

  @Override
  public void setNClob(int index, Reader value, long length) throws SQLException {
	setValue(index, value, length, PreparedStatement::setNClob, ResultSet::updateNClob, RowSet::setNClob);
  }

  @Override
  public void setSQLXML(int index, SQLXML value) throws SQLException {
	setValue(index, value, PreparedStatement::setSQLXML, ResultSet::updateSQLXML, RowSet::setSQLXML);
  }

  @Override
  public void setObject(int index, Object value, int targetSqlType, int scaleOrLength) throws SQLException {
	if (null != preparedStatement) preparedStatement.setObject(index, value, targetSqlType, scaleOrLength);
	else if (null != callableStatement) callableStatement.setObject(index, value, targetSqlType, scaleOrLength);
	else if (null != resultSet) {
	  try {
		resultSet.updateObject(index, value, JDBCType.valueOf(targetSqlType), scaleOrLength);
	  } catch (IllegalArgumentException | SQLFeatureNotSupportedException e) {
		resultSet.updateObject(index, value, scaleOrLength);
	  }
	} else if (null != rowSet) rowSet.setObject(index, value, targetSqlType, scaleOrLength);
  }

  @Override
  public void setAsciiStream(int index, InputStream value, long length) throws SQLException {
	setValue(index, value, length, PreparedStatement::setAsciiStream, ResultSet::updateAsciiStream, (rs, i, val, size) -> rs.setAsciiStream(i, val, size.intValue()));
  }

  @Override
  public void setBinaryStream(int index, InputStream value, long length) throws SQLException {
	setValue(index, value, length, PreparedStatement::setBinaryStream, ResultSet::updateBinaryStream, (rs, i, val, size) -> rs.setBinaryStream(i, val, size.intValue()));
  }

  @Override
  public void setCharacterStream(int index, Reader value, long length) throws SQLException {
	setValue(index, value, length, PreparedStatement::setCharacterStream, ResultSet::updateCharacterStream, (rs, i, val, size) -> rs.setCharacterStream(i, val, size.intValue()));
  }

  @Override
  public void setAsciiStream(int index, InputStream value) throws SQLException {
	setValue(index, value, PreparedStatement::setAsciiStream, ResultSet::updateAsciiStream, RowSet::setAsciiStream);
  }

  @Override
  public void setBinaryStream(int index, InputStream value) throws SQLException {
	setValue(index, value, PreparedStatement::setBinaryStream, ResultSet::updateBinaryStream, RowSet::setBinaryStream);
  }

  @Override
  public void setCharacterStream(int index, Reader value) throws SQLException {
	setValue(index, value, PreparedStatement::setCharacterStream, ResultSet::updateCharacterStream, RowSet::setCharacterStream);
  }

  @Override
  public void setNCharacterStream(int index, Reader value) throws SQLException {
	setValue(index, value, PreparedStatement::setNCharacterStream, ResultSet::updateNCharacterStream, RowSet::setNCharacterStream);
  }

  @Override
  public void setClob(int index, Reader value) throws SQLException {
	setValue(index, value, PreparedStatement::setClob, ResultSet::updateClob, RowSet::setClob);
  }

  @Override
  public void setBlob(int index, InputStream value) throws SQLException {
	setValue(index, value, PreparedStatement::setBlob, ResultSet::updateBlob, RowSet::setBlob);
  }

  @Override
  public void setNClob(int index, Reader value) throws SQLException {
	setValue(index, value, PreparedStatement::setNClob, ResultSet::updateNClob, RowSet::setNClob);
  }

  @Override
  public void setObject(int index, Object value, SQLType targetSqlType, int scaleOrLength) throws SQLException {
	if (null != preparedStatement) preparedStatement.setObject(index, value, targetSqlType, scaleOrLength);
	else if (null != callableStatement) callableStatement.setObject(index, value, targetSqlType, scaleOrLength);
	else if (null != resultSet) resultSet.updateObject(index, value, targetSqlType, scaleOrLength);
	else if (null != rowSet) rowSet.setObject(index, value, targetSqlType.getVendorTypeNumber(), scaleOrLength);
  }

  @Override
  public void setObject(int index, Object value, SQLType targetSqlType) throws SQLException {
	try {
	  if (null != preparedStatement) preparedStatement.setObject(index, value, targetSqlType);
	  else if (null != callableStatement) callableStatement.setObject(index, value, targetSqlType);
	  else if (null != resultSet) resultSet.updateObject(index, value, targetSqlType);
	  else if (null != rowSet) {
		try {
		  rowSet.setObject(index, value, targetSqlType.getVendorTypeNumber());
		} catch (SQLFeatureNotSupportedException e) {
		  rowSet.setObject(index, value);
		}
	  }
	} catch (SQLFeatureNotSupportedException e) {
	  setObject(index, value, targetSqlType.getVendorTypeNumber());
	}
  }

  @Override
  public void setArray(String name, Array value) throws SQLException {
	setValue(name, value, PreparedStatement::setArray, (pst, n, v) -> pst.setArray(indexOf(n), v), ResultSet::updateArray, (rs, n, v) -> rs.setArray(indexOf(n), v));
  }

  @Override
  public void setAsciiStream(String name, InputStream value) throws SQLException {
	setValue(name, value, PreparedStatement::setAsciiStream, CallableStatement::setAsciiStream, ResultSet::updateAsciiStream, RowSet::setAsciiStream);
  }

  @Override
  public void setAsciiStream(String name, InputStream value, int length) throws SQLException {
	setValue(name, value, length, PreparedStatement::setAsciiStream, CallableStatement::setAsciiStream, ResultSet::updateAsciiStream, RowSet::setAsciiStream);
  }

  @Override
  public void setAsciiStream(String name, InputStream value, long length) throws SQLException {
	setValue(name, value, length, PreparedStatement::setAsciiStream, CallableStatement::setAsciiStream, ResultSet::updateAsciiStream, (rs, n, v, l) -> rs.setAsciiStream(n, v, l.intValue()));
  }

  @Override
  public void setBigDecimal(String name, BigDecimal value) throws SQLException {
	setValue(name, value, PreparedStatement::setBigDecimal, CallableStatement::setBigDecimal, ResultSet::updateBigDecimal, RowSet::setBigDecimal);
  }

  @Override
  public void setBinaryStream(String name, InputStream value) throws SQLException {
	setValue(name, value, PreparedStatement::setBinaryStream, CallableStatement::setBinaryStream, ResultSet::updateBinaryStream, RowSet::setBinaryStream);
  }

  @Override
  public void setBinaryStream(String name, InputStream value, int length) throws SQLException {
	setValue(name, value, length, PreparedStatement::setBinaryStream, CallableStatement::setBinaryStream, ResultSet::updateBinaryStream, RowSet::setBinaryStream);
  }

  @Override
  public void setBinaryStream(String name, InputStream value, long length) throws SQLException {
	setValue(name, value, length, PreparedStatement::setBinaryStream, CallableStatement::setBinaryStream, ResultSet::updateBinaryStream, (rs, n, v, l) -> rs.setBinaryStream(n, v, l.intValue()));
  }

  @Override
  public void setBlob(String name, Blob value) throws SQLException {
	setValue(name, value, PreparedStatement::setBlob, CallableStatement::setBlob, ResultSet::updateBlob, RowSet::setBlob);
  }

  @Override
  public void setBlob(String name, InputStream value) throws SQLException {
	setValue(name, value, PreparedStatement::setBlob, CallableStatement::setBlob, ResultSet::updateBlob, RowSet::setBlob);
  }

  @Override
  public void setBlob(String name, InputStream value, long length) throws SQLException {
	setValue(name, value, length, PreparedStatement::setBlob, CallableStatement::setBlob, ResultSet::updateBlob, RowSet::setBlob);
  }

  @Override
  public void setBoolean(String name, boolean value) throws SQLException {
	setValue(name, value, PreparedStatement::setBoolean, CallableStatement::setBoolean, ResultSet::updateBoolean, RowSet::setBoolean);
  }

  @Override
  public void setByte(String name, byte value) throws SQLException {
	setValue(name, value, PreparedStatement::setByte, CallableStatement::setByte, ResultSet::updateByte, RowSet::setByte);
  }

  @Override
  public void setBytes(String name, byte[] value) throws SQLException {
	setValue(name, value, PreparedStatement::setBytes, CallableStatement::setBytes, ResultSet::updateBytes, RowSet::setBytes);
  }

  @Override
  public void setCharacterStream(String name, Reader value) throws SQLException {
	setValue(name, value, PreparedStatement::setCharacterStream, CallableStatement::setCharacterStream, ResultSet::updateCharacterStream, RowSet::setCharacterStream);
  }

  @Override
  public void setCharacterStream(String name, Reader value, int length) throws SQLException {
	setValue(name, value, length, PreparedStatement::setCharacterStream, CallableStatement::setCharacterStream, ResultSet::updateCharacterStream, RowSet::setCharacterStream);
  }

  @Override
  public void setCharacterStream(String name, Reader value, long length) throws SQLException {
	setValue(name, value, length, PreparedStatement::setCharacterStream, CallableStatement::setCharacterStream, ResultSet::updateCharacterStream, (rs, n, v, l) -> rs.setCharacterStream(n, v, l.intValue()));
  }

  @Override
  public void setClob(String name, Clob value) throws SQLException {
	setValue(name, value, PreparedStatement::setClob, CallableStatement::setClob, ResultSet::updateClob, RowSet::setClob);
  }

  @Override
  public void setClob(String name, Reader value) throws SQLException {
	setValue(name, value, PreparedStatement::setClob, CallableStatement::setClob, ResultSet::updateClob, RowSet::setClob);
  }

  @Override
  public void setClob(String name, Reader value, long length) throws SQLException {
	setValue(name, value, length, PreparedStatement::setClob, CallableStatement::setClob, ResultSet::updateClob, RowSet::setClob);
  }

  @Override
  public void setDate(String name, Date value) throws SQLException {
	setValue(name, value, PreparedStatement::setDate, CallableStatement::setDate, ResultSet::updateDate, RowSet::setDate);
  }

  @Override
  public void setDate(String name, Date value, Calendar cal) throws SQLException {
	setValue(name, value, cal, PreparedStatement::setDate, CallableStatement::setDate, (rs, n, v, c) -> rs.updateDate(n, v), RowSet::setDate);
  }

  @Override
  public void setDouble(String name, double value) throws SQLException {
	setValue(name, value, PreparedStatement::setDouble, CallableStatement::setDouble, ResultSet::updateDouble, RowSet::setDouble);
  }

  @Override
  public void setFloat(String name, float value) throws SQLException {
	setValue(name, value, PreparedStatement::setFloat, CallableStatement::setFloat, ResultSet::updateFloat, RowSet::setFloat);
  }

  @Override
  public void setInt(String name, int value) throws SQLException {
	setValue(name, value, PreparedStatement::setInt, CallableStatement::setInt, ResultSet::updateInt, RowSet::setInt);
  }

  @Override
  public void setLong(String name, long value) throws SQLException {
	setValue(name, value, PreparedStatement::setLong, CallableStatement::setLong, ResultSet::updateLong, RowSet::setLong);
  }

  @Override
  public void setNCharacterStream(String name, Reader value) throws SQLException {
	setValue(name, value, PreparedStatement::setNCharacterStream, CallableStatement::setNCharacterStream, ResultSet::updateNCharacterStream, RowSet::setNCharacterStream);
  }

  @Override
  public void setNCharacterStream(String name, Reader value, long length) throws SQLException {
	setValue(name, value, length, PreparedStatement::setNCharacterStream, CallableStatement::setNCharacterStream, ResultSet::updateNCharacterStream, RowSet::setNCharacterStream);
  }

  @Override
  public void setNClob(String name, NClob value) throws SQLException {
	setValue(name, value, PreparedStatement::setNClob, CallableStatement::setClob, ResultSet::updateClob, RowSet::setNClob);
  }

  @Override
  public void setNClob(String name, Reader value) throws SQLException {
	setValue(name, value, PreparedStatement::setNClob, CallableStatement::setClob, ResultSet::updateClob, RowSet::setNClob);
  }

  @Override
  public void setNClob(String name, Reader value, long length) throws SQLException {
	setValue(name, value, length, PreparedStatement::setNClob, CallableStatement::setClob, ResultSet::updateClob, RowSet::setNClob);
  }

  @Override
  public void setNString(String name, String value) throws SQLException {
	setValue(name, value, PreparedStatement::setNString, CallableStatement::setNString, ResultSet::updateNString, RowSet::setNString);
  }

  @Override
  public void setNull(String name, int sqlType) throws SQLException {
	setNull(name, sqlType, null);
  }

  @Override
  public void setNull(String name, int sqlType, String typeName) throws SQLException {
	setNull(indexOf(name), sqlType, typeName);
  }

  @Override
  public void setObject(String name, Object value) throws SQLException {
	setValue(name, value, PreparedStatement::setObject, CallableStatement::setObject, ResultSet::updateObject, RowSet::setObject);
  }

  @Override
  public void setObject(String name, Object value, int targetSqlType) throws SQLException {
	setValue(name, value, targetSqlType, PreparedStatement::setObject, CallableStatement::setObject, ResultSet::updateObject, RowSet::setObject);
  }

  @Override
  public void setObject(String name, Object value, int targetSqlType, int scale) throws SQLException {
	setObject(indexOf(name), value, targetSqlType, scale);
  }

  @Override
  public void setObject(String name, Object value, SQLType targetSqlType) throws SQLException {
	setValue(name, value, PreparedStatement::setObject, CallableStatement::setObject, ResultSet::updateObject, RowSet::setObject);
  }

  @Override
  public void setObject(String name, Object value, SQLType targetSqlType, int scaleOrLength) throws SQLException {
	setObject(indexOf(name), value, targetSqlType, scaleOrLength);
  }

  @Override
  public void setRowId(String name, RowId value) throws SQLException {
	setValue(name, value, PreparedStatement::setRowId, CallableStatement::setRowId, ResultSet::updateRowId, RowSet::setRowId);
  }

  @Override
  public void setShort(String name, short value) throws SQLException {
	setValue(name, value, PreparedStatement::setShort, CallableStatement::setShort, ResultSet::updateShort, RowSet::setShort);
  }

  @Override
  public void setSQLXML(String name, SQLXML value) throws SQLException {
	setValue(name, value, PreparedStatement::setSQLXML, CallableStatement::setSQLXML, ResultSet::updateSQLXML, RowSet::setSQLXML);
  }

  @Override
  public void setString(String name, String value) throws SQLException {
	setValue(name, value, PreparedStatement::setString, CallableStatement::setString, ResultSet::updateString, RowSet::setString);
  }

  @Override
  public void setTime(String name, Time value) throws SQLException {
	setValue(name, value, PreparedStatement::setTime, CallableStatement::setTime, ResultSet::updateTime, RowSet::setTime);
  }

  @Override
  public void setTime(String name, Time value, Calendar cal) throws SQLException {
	setValue(name, value, cal, PreparedStatement::setTime, CallableStatement::setTime, (rs, n, v, c) -> rs.updateTime(n, v), RowSet::setTime);
  }

  @Override
  public void setTimestamp(String name, Timestamp value) throws SQLException {
	setValue(name, value, PreparedStatement::setTimestamp, CallableStatement::setTimestamp, ResultSet::updateTimestamp, RowSet::setTimestamp);
  }

  @Override
  public void setTimestamp(String name, Timestamp value, Calendar cal) throws SQLException {
	setValue(name, value, cal, PreparedStatement::setTimestamp, CallableStatement::setTimestamp, (rs, n, v, c) -> rs.updateTimestamp(n, v), RowSet::setTimestamp);
  }

  @Override
  public void setURL(String name, URL value) throws SQLException {
	setValue(name, value, PreparedStatement::setURL, CallableStatement::setURL, ResultSet::updateObject, (rs, n, v) -> rs.setURL(indexOf(n), v));
  }
}
