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

import buckelieg.jdbc.fn.TryBiFunction;
import buckelieg.jdbc.fn.TryTriFunction;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.Map;

import static java.util.Objects.requireNonNull;

final class ValueGetters implements ValueReader {

  private final ResultSet resultSet;

  private final CallableStatement callableStatement;

  private final Metadata metadata;

  private ValueGetters(Metadata metadata, ResultSet resultSet, CallableStatement callableStatement) {
	this.metadata = requireNonNull(metadata, "Metadata must be provided");
	this.resultSet = resultSet;
	this.callableStatement = callableStatement;
  }

  static <T extends ResultSet> ValueReader reader(Metadata metadata, T resultSet) {
	return new ValueGetters(metadata, requireNonNull(resultSet, "ResultSet instance must be provided"), null);
  }

  static ValueReader reader(Metadata metadata, CallableStatement statement) {
	return new ValueGetters(metadata, null, requireNonNull(statement, "Statement instance must be provided"));
  }

  private <T> T getValue(
		  int parameterIndex,
		  TryBiFunction<CallableStatement, Integer, T, SQLException> statementGetter,
		  TryBiFunction<ResultSet, Integer, T, SQLException> resultSetGetter
  ) throws SQLException {
	if (null != callableStatement) return statementGetter.apply(callableStatement, parameterIndex);
	else if (null != resultSet) return resultSetGetter.apply(resultSet, parameterIndex);
	return null;
  }

  private <T> T getValue(
		  String parameterName,
		  TryBiFunction<CallableStatement, String, T, SQLException> statementGetter,
		  TryBiFunction<ResultSet, String, T, SQLException> resultSetGetter
  ) throws SQLException {
	if (null != callableStatement) return statementGetter.apply(callableStatement, parameterName);
	else if (null != resultSet) return resultSetGetter.apply(resultSet, parameterName);
	return null;
  }

  private <T, V> T getValue(
		  int parameterIndex, V value,
		  TryTriFunction<CallableStatement, Integer, V, T, SQLException> statementGetter,
		  TryTriFunction<ResultSet, Integer, V, T, SQLException> resultSetGetter
  ) throws SQLException {
	if (null != callableStatement) return statementGetter.apply(callableStatement, parameterIndex, value);
	else if (null != resultSet) return resultSetGetter.apply(resultSet, parameterIndex, value);
	return null;
  }

  private <T, V> T getValue(
		  String parameterName, V value,
		  TryTriFunction<CallableStatement, String, V, T, SQLException> statementGetter,
		  TryTriFunction<ResultSet, String, V, T, SQLException> resultSetGetter
  ) throws SQLException {
	if (null != callableStatement) return statementGetter.apply(callableStatement, parameterName, value);
	else if (null != resultSet) return resultSetGetter.apply(resultSet, parameterName, value);
	return null;
  }

  @Nonnull
  @Override
  public Metadata meta() {
	return metadata;
  }

  @Override
  public Array getArray(int index) throws SQLException {
	return getValue(index, CallableStatement::getArray, ResultSet::getArray);
  }

  @Override
  public InputStream getAsciiStream(int parameterIndex) throws SQLException {
	return getValue(parameterIndex, (stmt, index) -> new ByteArrayInputStream(stmt.getBytes(index)), ResultSet::getAsciiStream);
  }

  @Override
  public BigDecimal getBigDecimal(int index) throws SQLException {
	return getValue(index, CallableStatement::getBigDecimal, ResultSet::getBigDecimal);
  }

  @Deprecated
  @Override
  public BigDecimal getBigDecimal(int index, int scale) throws SQLException {
	if (null != callableStatement) return callableStatement.getBigDecimal(index, scale);
	else if (null != resultSet) return resultSet.getBigDecimal(index, scale);
	return null;
  }

  @Override
  public InputStream getBinaryStream(int index) throws SQLException {
	return getValue(index, (stmt, i) -> new ByteArrayInputStream(stmt.getBytes(i)), ResultSet::getBinaryStream);
  }

  @Override
  public Blob getBlob(int index) throws SQLException {
	return getValue(index, CallableStatement::getBlob, ResultSet::getBlob);
  }

  @Override
  public boolean getBoolean(int index) throws SQLException {
	return getValue(index, CallableStatement::getBoolean, ResultSet::getBoolean);
  }

  @Override
  public byte getByte(int index) throws SQLException {
	return getValue(index, CallableStatement::getByte, ResultSet::getByte);
  }

  @Override
  public byte[] getBytes(int index) throws SQLException {
	return getValue(index, CallableStatement::getBytes, ResultSet::getBytes);
  }

  @Override
  public Reader getCharacterStream(int index) throws SQLException {
	return getValue(index, CallableStatement::getCharacterStream, ResultSet::getCharacterStream);
  }

  @Override
  public Clob getClob(int index) throws SQLException {
	return getValue(index, CallableStatement::getClob, ResultSet::getClob);
  }

  @Override
  public Date getDate(int index) throws SQLException {
	return getValue(index, CallableStatement::getDate, ResultSet::getDate);
  }

  @Override
  public Date getDate(int index, Calendar cal) throws SQLException {
	return getValue(index, cal, CallableStatement::getDate, ResultSet::getDate);
  }

  @Override
  public double getDouble(int index) throws SQLException {
	return getValue(index, CallableStatement::getDouble, ResultSet::getDouble);
  }

  @Override
  public float getFloat(int index) throws SQLException {
	return getValue(index, CallableStatement::getFloat, ResultSet::getFloat);
  }

  @Override
  public int getInt(int index) throws SQLException {
	return getValue(index, CallableStatement::getInt, ResultSet::getInt);
  }

  @Override
  public long getLong(int index) throws SQLException {
	return getValue(index, CallableStatement::getLong, ResultSet::getLong);
  }

  @Override
  public Reader getNCharacterStream(int index) throws SQLException {
	return getValue(index, CallableStatement::getNCharacterStream, ResultSet::getNCharacterStream);
  }

  @Override
  public NClob getNClob(int index) throws SQLException {
	return getValue(index, CallableStatement::getNClob, ResultSet::getNClob);
  }

  @Override
  public String getNString(int index) throws SQLException {
	return getValue(index, CallableStatement::getNString, ResultSet::getNString);
  }

  @Override
  public Object getObject(int index) throws SQLException {
	return getValue(index, CallableStatement::getObject, ResultSet::getObject);
  }

  @Override
  public <T> T getObject(int index, Class<T> type) throws SQLException {
	if (null != callableStatement) return callableStatement.getObject(index, type);
	else if (null != resultSet) return resultSet.getObject(index, type);
	return null;
  }

  @Override
  public Object getObject(int index, Map<String, Class<?>> map) throws SQLException {
	if (null != callableStatement) return callableStatement.getObject(index, map);
	else if (null != resultSet) return resultSet.getObject(index, map);
	return null;
  }

  @Override
  public Ref getRef(int index) throws SQLException {
	return getValue(index, CallableStatement::getRef, ResultSet::getRef);
  }

  @Override
  public RowId getRowId(int index) throws SQLException {
	return getValue(index, CallableStatement::getRowId, ResultSet::getRowId);
  }

  @Override
  public short getShort(int index) throws SQLException {
	return getValue(index, CallableStatement::getShort, ResultSet::getShort);
  }

  @Override
  public SQLXML getSQLXML(int index) throws SQLException {
	return getValue(index, CallableStatement::getSQLXML, ResultSet::getSQLXML);
  }

  @Override
  public String getString(int index) throws SQLException {
	return getValue(index, CallableStatement::getString, ResultSet::getString);
  }

  @Override
  public Time getTime(int index) throws SQLException {
	return getValue(index, CallableStatement::getTime, ResultSet::getTime);
  }

  @Override
  public Time getTime(int index, Calendar cal) throws SQLException {
	return getValue(index, cal, CallableStatement::getTime, ResultSet::getTime);
  }

  @Override
  public Timestamp getTimestamp(int index) throws SQLException {
	return getValue(index, CallableStatement::getTimestamp, ResultSet::getTimestamp);
  }

  @Override
  public Timestamp getTimestamp(int index, Calendar cal) throws SQLException {
	return getValue(index, cal, CallableStatement::getTimestamp, ResultSet::getTimestamp);
  }

  @SuppressWarnings("deprecation")
  @Override
  public InputStream getUnicodeStream(int index) throws SQLException {
	return getValue(index, (stmt, i) -> stmt.getBlob(i).getBinaryStream(), ResultSet::getUnicodeStream);
  }

  @Override
  public URL getURL(int index) throws SQLException {
	return getValue(index, CallableStatement::getURL, ResultSet::getURL);
  }

  @Override
  public Array getArray(String name) throws SQLException {
	return getValue(name, CallableStatement::getArray, ResultSet::getArray);
  }

  @Override
  public InputStream getAsciiStream(String name) throws SQLException {
	return getValue(name, (stmt, n) -> new ByteArrayInputStream(stmt.getBytes(n)), ResultSet::getAsciiStream);
  }

  @Override
  public BigDecimal getBigDecimal(String name) throws SQLException {
	return getValue(name, CallableStatement::getBigDecimal, ResultSet::getBigDecimal);
  }

  @Deprecated
  @Override
  public BigDecimal getBigDecimal(String name, int scale) throws SQLException {
	if (null != callableStatement) {
	  ResultSetMetaData meta = callableStatement.getMetaData();
	  for (int index = 1; index <= meta.getColumnCount(); index++) {
		if (name.equals(meta.getColumnName(index))) return callableStatement.getBigDecimal(index, scale);
	  }
	  return callableStatement.getBigDecimal(name);
	} else if (null != resultSet) return resultSet.getBigDecimal(name, scale);
	return null;
  }

  @Override
  public InputStream getBinaryStream(String name) throws SQLException {
	return getValue(name, (stmt, c) -> new ByteArrayInputStream(stmt.getBytes(c)), ResultSet::getBinaryStream);
  }

  @Override
  public Blob getBlob(String name) throws SQLException {
	return getValue(name, CallableStatement::getBlob, ResultSet::getBlob);
  }

  @Override
  public boolean getBoolean(String name) throws SQLException {
	return getValue(name, CallableStatement::getBoolean, ResultSet::getBoolean);
  }

  @Override
  public byte getByte(String name) throws SQLException {
	return getValue(name, CallableStatement::getByte, ResultSet::getByte);
  }

  @Override
  public byte[] getBytes(String name) throws SQLException {
	return getValue(name, CallableStatement::getBytes, ResultSet::getBytes);
  }

  @Override
  public Reader getCharacterStream(String name) throws SQLException {
	return getValue(name, CallableStatement::getCharacterStream, ResultSet::getCharacterStream);
  }

  @Override
  public Clob getClob(String name) throws SQLException {
	return getValue(name, CallableStatement::getClob, ResultSet::getClob);
  }

  @Override
  public Date getDate(String name) throws SQLException {
	return getValue(name, CallableStatement::getDate, ResultSet::getDate);
  }

  @Override
  public Date getDate(String name, Calendar cal) throws SQLException {
	return getValue(name, cal, CallableStatement::getDate, ResultSet::getDate);
  }

  @Override
  public double getDouble(String name) throws SQLException {
	return getValue(name, CallableStatement::getDouble, ResultSet::getDouble);
  }

  @Override
  public float getFloat(String name) throws SQLException {
	return getValue(name, CallableStatement::getFloat, ResultSet::getFloat);
  }

  @Override
  public int getInt(String name) throws SQLException {
	return getValue(name, CallableStatement::getInt, ResultSet::getInt);
  }

  @Override
  public long getLong(String name) throws SQLException {
	return getValue(name, CallableStatement::getLong, ResultSet::getLong);
  }

  @Override
  public Reader getNCharacterStream(String name) throws SQLException {
	return getValue(name, CallableStatement::getNCharacterStream, ResultSet::getNCharacterStream);
  }

  @Override
  public NClob getNClob(String name) throws SQLException {
	return getValue(name, CallableStatement::getNClob, ResultSet::getNClob);
  }

  @Override
  public String getNString(String name) throws SQLException {
	return getValue(name, CallableStatement::getNString, ResultSet::getNString);
  }

  @Override
  public Object getObject(String name) throws SQLException {
	return getValue(name, CallableStatement::getObject, ResultSet::getObject);
  }

  @Override
  public <T> T getObject(String name, Class<T> type) throws SQLException {
	if (null != callableStatement) return callableStatement.getObject(name, type);
	else if (null != resultSet) return resultSet.getObject(name, type);
	return null;
  }

  @Override
  public Object getObject(String name, Map<String, Class<?>> map) throws SQLException {
	if (null != callableStatement) return callableStatement.getObject(name, map);
	else if (null != resultSet) return resultSet.getObject(name, map);
	return null;
  }

  @Override
  public Ref getRef(String name) throws SQLException {
	return getValue(name, CallableStatement::getRef, ResultSet::getRef);
  }

  @Override
  public RowId getRowId(String name) throws SQLException {
	return getValue(name, CallableStatement::getRowId, ResultSet::getRowId);
  }

  @Override
  public short getShort(String name) throws SQLException {
	return getValue(name, CallableStatement::getShort, ResultSet::getShort);
  }

  @Override
  public SQLXML getSQLXML(String name) throws SQLException {
	return getValue(name, CallableStatement::getSQLXML, ResultSet::getSQLXML);
  }

  @Override
  public String getString(String name) throws SQLException {
	return getValue(name, CallableStatement::getString, ResultSet::getString);
  }

  @Override
  public Time getTime(String name) throws SQLException {
	return getValue(name, CallableStatement::getTime, ResultSet::getTime);
  }

  @Override
  public Time getTime(String name, Calendar cal) throws SQLException {
	return getValue(name, cal, CallableStatement::getTime, ResultSet::getTime);
  }

  @Override
  public Timestamp getTimestamp(String name) throws SQLException {
	return getValue(name, CallableStatement::getTimestamp, ResultSet::getTimestamp);
  }

  @Override
  public Timestamp getTimestamp(String name, Calendar cal) throws SQLException {
	return getValue(name, cal, CallableStatement::getTimestamp, ResultSet::getTimestamp);
  }

  @Deprecated
  @Override
  public InputStream getUnicodeStream(String name) throws SQLException {
	return getValue(name, (stmt, n) -> stmt.getBlob(n).getBinaryStream(), ResultSet::getUnicodeStream);
  }

  @Override
  public URL getURL(String name) throws SQLException {
	return getValue(name, CallableStatement::getURL, ResultSet::getURL);
  }
}
