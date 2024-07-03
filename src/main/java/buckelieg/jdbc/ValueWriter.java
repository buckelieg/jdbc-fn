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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;

/**
 * An abstraction for "write" operations on either {@linkplain ResultSet} or appropriate {@linkplain Statement}s
 * <br/>This is needed to "normalize" raw JDBC API with common methods
 */
public interface ValueWriter {

  /**
   * Returns a metadata for this writer object
   *
   * @return a {@linkplain Metadata} for this ValueWriter
   */
  @Nonnull
  Metadata meta();

  /**
   * @see PreparedStatement#setNull(int, int)
   * @see CallableStatement#setNull(int, int)
   * @see ResultSet#updateNull(int)
   * @see javax.sql.RowSet#setNull(int, int)
   */
  void setNull(int index, int sqlType) throws SQLException;

  /**
   * @see PreparedStatement#setBoolean(int, boolean)
   * @see CallableStatement#setBoolean(int, boolean)
   * @see ResultSet#updateBoolean(int, boolean)
   * @see javax.sql.RowSet#setBoolean(int, boolean)
   */
  void setBoolean(int index, boolean value) throws SQLException;

  /**
   * @see PreparedStatement#setByte(int, byte)
   * @see CallableStatement#setByte(int, byte)
   * @see ResultSet#updateByte(int, byte)
   * @see javax.sql.RowSet#setByte(int, byte)
   */
  void setByte(int index, byte value) throws SQLException;

  /**
   * @see PreparedStatement#setShort(int, short)
   * @see CallableStatement#setShort(int, short)
   * @see ResultSet#updateShort(int, short)
   * @see javax.sql.RowSet#setShort(int, short)
   */
  void setShort(int index, short value) throws SQLException;

  /**
   * @see PreparedStatement#setInt(int, int)
   * @see CallableStatement#setInt(int, int)
   * @see ResultSet#updateInt(int, int)
   * @see javax.sql.RowSet#setInt(int, int)
   */
  void setInt(int index, int value) throws SQLException;

  /**
   * @see PreparedStatement#setLong(int, long)
   * @see CallableStatement#setLong(int, long)
   * @see ResultSet#updateLong(int, long)
   * @see javax.sql.RowSet#setLong(int, long)
   */
  void setLong(int index, long value) throws SQLException;

  /**
   * @see PreparedStatement#setFloat(int, float)
   * @see CallableStatement#setFloat(int, float)
   * @see ResultSet#updateFloat(int, float)
   * @see javax.sql.RowSet#setFloat(int, float)
   */
  void setFloat(int index, float value) throws SQLException;

  /**
   * @see PreparedStatement#setDouble(int, double)
   * @see CallableStatement#setDouble(int, double)
   * @see ResultSet#updateDouble(int, double)
   * @see javax.sql.RowSet#setDouble(int, double)
   */
  void setDouble(int index, double value) throws SQLException;

  /**
   * @see PreparedStatement#setBigDecimal(int, BigDecimal)
   * @see CallableStatement#setBigDecimal(int, BigDecimal)
   * @see ResultSet#updateBigDecimal(int, BigDecimal)
   * @see javax.sql.RowSet#setBigDecimal(int, BigDecimal)
   */
  void setBigDecimal(int index, BigDecimal value) throws SQLException;

  /**
   * @see PreparedStatement#setString(int, String)
   * @see CallableStatement#setString(int, String)
   * @see ResultSet#updateString(int, String)
   * @see javax.sql.RowSet#setString(int, String)
   */
  void setString(int index, String value) throws SQLException;

  /**
   * @see PreparedStatement#setBytes(int, byte[])
   * @see CallableStatement#setBytes(int, byte[])
   * @see ResultSet#updateBytes(int, byte[])
   * @see javax.sql.RowSet#setBytes(int, byte[])
   */
  void setBytes(int index, byte[] value) throws SQLException;

  /**
   * @see PreparedStatement#setDate(int, Date)
   * @see CallableStatement#setDate(int, Date)
   * @see ResultSet#updateDate(int, Date)
   * @see javax.sql.RowSet#setDate(int, Date)
   */
  void setDate(int index, Date value) throws SQLException;

  /**
   * @see PreparedStatement#setTime(int, Time)
   * @see CallableStatement#setTime(int, Time)
   * @see ResultSet#updateTime(int, Time)
   * @see javax.sql.RowSet#setTime(int, Time)
   */
  void setTime(int index, Time value) throws SQLException;

  /**
   * @see PreparedStatement#setTimestamp(int, Timestamp)
   * @see CallableStatement#setTimestamp(int, Timestamp)
   * @see ResultSet#updateTimestamp(int, Timestamp)
   * @see javax.sql.RowSet#setTimestamp(int, Timestamp)
   */
  void setTimestamp(int index, Timestamp value) throws SQLException;

  /**
   * @see PreparedStatement#setAsciiStream(int, InputStream, int)
   * @see CallableStatement#setAsciiStream(int, InputStream, int)
   * @see ResultSet#updateAsciiStream(int, InputStream, int)
   * @see javax.sql.RowSet#setAsciiStream(int, InputStream, int)
   */
  void setAsciiStream(int index, InputStream value, int length) throws SQLException;

  /**
   * @see PreparedStatement#setUnicodeStream(int, InputStream, int)
   * @see CallableStatement#setUnicodeStream(int, InputStream, int)
   * @see ResultSet#updateCharacterStream(int, Reader, int)
   * @see javax.sql.RowSet#setCharacterStream(int, Reader, int)
   * @see InputStreamReader
   */
  @Deprecated
  void setUnicodeStream(int index, InputStream value, int length) throws SQLException;

  /**
   * @see PreparedStatement#setBinaryStream(int, InputStream, int)
   * @see CallableStatement#setBinaryStream(int, InputStream, int)
   * @see ResultSet#updateBinaryStream(int, InputStream, int)
   * @see javax.sql.RowSet#setBinaryStream(int, InputStream, int)
   */
  void setBinaryStream(int index, InputStream value, int length) throws SQLException;

  /**
   * @see PreparedStatement#setObject(int, Object, int)
   * @see CallableStatement#setObject(int, Object, int)
   * @see ResultSet#updateObject(int, Object, int)
   * @see javax.sql.RowSet#setObject(int, Object, int)
   */
  void setObject(int index, Object value, int targetSqlType) throws SQLException;

  /**
   * @see PreparedStatement#setObject(int, Object)
   * @see CallableStatement#setObject(int, Object)
   * @see ResultSet#updateObject(int, Object)
   * @see javax.sql.RowSet#setObject(int, Object)
   */
  void setObject(int index, Object value) throws SQLException;

  /**
   * @see PreparedStatement#setCharacterStream(int, Reader, int)
   * @see CallableStatement#setCharacterStream(int, Reader, int)
   * @see ResultSet#updateCharacterStream(int, Reader, int)
   * @see javax.sql.RowSet#setCharacterStream(int, Reader, int)
   */
  void setCharacterStream(int index, Reader value, int length) throws SQLException;

  /**
   * @see PreparedStatement#setRef(int, Ref)
   * @see CallableStatement#setRef(int, Ref)
   * @see ResultSet#updateRef(int, Ref)
   * @see javax.sql.RowSet#setRef(int, Ref)
   */
  void setRef(int index, Ref value) throws SQLException;

  /**
   * @see PreparedStatement#setBlob(int, Blob)
   * @see CallableStatement#setBlob(int, Blob)
   * @see ResultSet#updateBlob(int, Blob)
   * @see javax.sql.RowSet#setBlob(int, Blob)
   */
  void setBlob(int index, Blob value) throws SQLException;

  /**
   * @see PreparedStatement#setClob(int, Clob)
   * @see CallableStatement#setClob(int, Clob)
   * @see ResultSet#updateClob(int, Clob)
   * @see javax.sql.RowSet#setClob(int, Clob)
   */
  void setClob(int index, Clob value) throws SQLException;

  /**
   * @see PreparedStatement#setArray(int, Array)
   * @see CallableStatement#setArray(int, Array)
   * @see ResultSet#updateArray(int, Array)
   * @see javax.sql.RowSet#setArray(int, Array)
   */
  void setArray(int index, Array value) throws SQLException;

  /**
   * @see PreparedStatement#setDate(int, Date, Calendar)
   * @see CallableStatement#setDate(int, Date, Calendar)
   * @see ResultSet#updateDate(int, Date)
   * @see javax.sql.RowSet#setDate(int, Date, Calendar)
   */
  void setDate(int index, Date value, Calendar cal) throws SQLException;

  /**
   * @see PreparedStatement#setTime(int, Time, Calendar)
   * @see CallableStatement#setTime(int, Time, Calendar)
   * @see ResultSet#updateTime(int, Time)
   * @see javax.sql.RowSet#setTime(int, Time, Calendar)
   */
  void setTime(int index, Time value, Calendar cal) throws SQLException;

  /**
   * @see PreparedStatement#setTimestamp(int, Timestamp, Calendar)
   * @see CallableStatement#setTimestamp(int, Timestamp, Calendar)
   * @see ResultSet#updateTimestamp(int, Timestamp)
   * @see javax.sql.RowSet#setTimestamp(int, Timestamp, Calendar)
   */
  void setTimestamp(int index, Timestamp value, Calendar cal) throws SQLException;

  /**
   * @see PreparedStatement#setNull(int, int, String)
   * @see CallableStatement#setNull(int, int, String)
   * @see ResultSet#updateNull(int)
   * @see javax.sql.RowSet#setNull(int, int, String)
   */
  void setNull(int index, int sqlType, String typeName) throws SQLException;

  /**
   * @see PreparedStatement#setURL(int, URL)
   * @see CallableStatement#setURL(int, URL)
   * @see ResultSet#updateObject(int, Object)
   * @see javax.sql.RowSet#setURL(int, URL)
   */
  void setURL(int index, URL value) throws SQLException;

  /**
   * @see PreparedStatement#setRowId(int, RowId)
   * @see CallableStatement#setRowId(int, RowId)
   * @see ResultSet#updateRowId(int, RowId)
   * @see javax.sql.RowSet#setRowId(int, RowId)
   */
  void setRowId(int index, RowId value) throws SQLException;

  /**
   * @see PreparedStatement#setNString(int, String)
   * @see CallableStatement#setNString(int, String)
   * @see ResultSet#updateNString(int, String)
   * @see javax.sql.RowSet#setNString(int, String)
   */
  void setNString(int index, String value) throws SQLException;

  /**
   * @see PreparedStatement#setNCharacterStream(int, Reader, long)
   * @see CallableStatement#setNCharacterStream(int, Reader, long)
   * @see ResultSet#updateNCharacterStream(int, Reader, long)
   * @see javax.sql.RowSet#setNCharacterStream(int, Reader, long)
   */
  void setNCharacterStream(int index, Reader value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setNClob(int, NClob)
   * @see CallableStatement#setNClob(int, NClob)
   * @see ResultSet#updateNClob(int, NClob)
   * @see javax.sql.RowSet#setNClob(int, NClob)
   */
  void setNClob(int index, NClob value) throws SQLException;

  /**
   * @see PreparedStatement#setClob(int, Reader, long)
   * @see CallableStatement#setClob(int, Reader, long)
   * @see ResultSet#updateClob(int, Clob)
   * @see javax.sql.RowSet#setClob(int, Reader, long)
   */
  void setClob(int index, Reader value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setBlob(int, InputStream, long)
   * @see CallableStatement#setBlob(int, InputStream, long)
   * @see ResultSet#updateBlob(int, InputStream, long)
   * @see javax.sql.RowSet#setBlob(int, InputStream, long)
   */
  void setBlob(int index, InputStream value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setNClob(int, Reader, long)
   * @see CallableStatement#setNClob(int, Reader, long)
   * @see ResultSet#updateNClob(String, Reader, long)
   * @see javax.sql.RowSet#setNClob(int, Reader, long)
   */
  void setNClob(int index, Reader value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setSQLXML(int, SQLXML)
   * @see CallableStatement#setSQLXML(int, SQLXML)
   * @see ResultSet#updateSQLXML(int, SQLXML)
   * @see javax.sql.RowSet#setSQLXML(int, SQLXML)
   */
  void setSQLXML(int index, SQLXML value) throws SQLException;

  /**
   * @see PreparedStatement#setObject(int, Object, int, int)
   * @see CallableStatement#setObject(int, Object, int, int)
   * @see ResultSet#updateObject(String, Object, SQLType)
   * @see ResultSet#updateObject(int, Object, int)
   * @see javax.sql.RowSet#setObject(int, Object, int, int)
   */
  void setObject(int index, Object value, int targetSqlType, int scaleOrLength) throws SQLException;

  /**
   * @see PreparedStatement#setAsciiStream(int, InputStream, long)
   * @see CallableStatement#setAsciiStream(int, InputStream, long)
   * @see ResultSet#updateNull(int)
   * @see javax.sql.RowSet#setAsciiStream(int, InputStream, int)
   */
  void setAsciiStream(int index, InputStream value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setBinaryStream(int, InputStream, long)
   * @see CallableStatement#setBinaryStream(int, InputStream, long)
   * @see ResultSet#updateBinaryStream(int, InputStream, long)
   * @see javax.sql.RowSet#setBinaryStream(int, InputStream, int)
   */
  void setBinaryStream(int index, InputStream value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setCharacterStream(int, Reader, long)
   * @see CallableStatement#setCharacterStream(int, Reader, long)
   * @see ResultSet#updateCharacterStream(int, Reader, long)
   * @see javax.sql.RowSet#setCharacterStream(int, Reader, int)
   */
  void setCharacterStream(int index, Reader value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setAsciiStream(int, InputStream)
   * @see CallableStatement#setAsciiStream(int, InputStream)
   * @see ResultSet#updateAsciiStream(int, InputStream)
   * @see javax.sql.RowSet#setAsciiStream(int, InputStream)
   */
  void setAsciiStream(int index, InputStream value) throws SQLException;

  /**
   * @see PreparedStatement#setBinaryStream(int, InputStream)
   * @see CallableStatement#setBinaryStream(int, InputStream)
   * @see ResultSet#updateBinaryStream(int, InputStream)
   * @see javax.sql.RowSet#setBinaryStream(int, InputStream)
   */
  void setBinaryStream(int index, InputStream value) throws SQLException;

  /**
   * @see PreparedStatement#setNull(int, int)
   * @see CallableStatement#setNull(int, int)
   * @see ResultSet#updateNull(int)
   * @see javax.sql.RowSet#setNull(int, int)
   */
  void setCharacterStream(int index, Reader value) throws SQLException;

  /**
   * @see PreparedStatement#setNull(int, int)
   * @see CallableStatement#setNull(int, int)
   * @see ResultSet#updateNull(int)
   * @see javax.sql.RowSet#setNull(int, int)
   */
  void setNCharacterStream(int index, Reader value) throws SQLException;

  /**
   * @see PreparedStatement#setNull(int, int)
   * @see CallableStatement#setNull(int, int)
   * @see ResultSet#updateNull(int)
   * @see javax.sql.RowSet#setNull(int, int)
   */
  void setClob(int index, Reader value) throws SQLException;

  /**
   * @see PreparedStatement#setBlob(int, InputStream)
   * @see CallableStatement#setBlob(int, InputStream)
   * @see ResultSet#updateBlob(int, InputStream)
   * @see javax.sql.RowSet#setBlob(int, InputStream)
   */
  void setBlob(int index, InputStream value) throws SQLException;

  /**
   * @see PreparedStatement#setNClob(int, Reader)
   * @see CallableStatement#setNClob(int, Reader)
   * @see ResultSet#updateNClob(int, Reader)
   * @see javax.sql.RowSet#setNClob(int, Reader)
   */
  void setNClob(int index, Reader value) throws SQLException;

  /**
   * @see PreparedStatement#setObject(int, Object, SQLType, int)
   * @see CallableStatement#setObject(int, Object, SQLType, int)
   * @see ResultSet#updateObject(String, Object, SQLType, int)
   * @see javax.sql.RowSet#setObject(int, Object, int, int)
   */
  void setObject(int index, Object value, SQLType targetSqlType, int scaleOrLength) throws SQLException;

  /**
   * @see PreparedStatement#setObject(int, Object, SQLType)
   * @see CallableStatement#setObject(int, Object, SQLType)
   * @see ResultSet#updateObject(String, Object, SQLType)
   * @see javax.sql.RowSet#setObject(int, Object, int)
   * @see javax.sql.RowSet#setObject(int, Object)
   */
  void setObject(int index, Object value, SQLType targetSqlType) throws SQLException;

  /**
   * @see PreparedStatement#setArray(int, Array)
   * @see CallableStatement#setArray(int, Array)
   * @see ResultSet#updateArray(String, Array)
   * @see javax.sql.RowSet#setArray(int, Array)
   */
  void setArray(String name, Array value) throws SQLException;

  /**
   * @see PreparedStatement#setAsciiStream(int, InputStream)
   * @see CallableStatement#setAsciiStream(String, InputStream)
   * @see ResultSet#updateAsciiStream(int, InputStream)
   * @see javax.sql.RowSet#setAsciiStream(int, InputStream)
   */
  void setAsciiStream(String name, InputStream value) throws SQLException;

  /**
   * @see PreparedStatement#setAsciiStream(int, InputStream, int)
   * @see CallableStatement#setAsciiStream(String, InputStream, int)
   * @see ResultSet#updateAsciiStream(String, InputStream, int)
   * @see javax.sql.RowSet#setAsciiStream(int, InputStream, int)
   */
  void setAsciiStream(String name, InputStream value, int length) throws SQLException;

  /**
   * @see PreparedStatement#setAsciiStream(int, InputStream, long)
   * @see CallableStatement#setAsciiStream(String, InputStream, long)
   * @see ResultSet#updateAsciiStream(String, InputStream, long)
   * @see javax.sql.RowSet#setAsciiStream(int, InputStream, int)
   */
  void setAsciiStream(String name, InputStream value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setBigDecimal(int, BigDecimal)
   * @see CallableStatement#setBigDecimal(String, BigDecimal)
   * @see ResultSet#updateNull(int)
   * @see javax.sql.RowSet#setBigDecimal(int, BigDecimal)
   */
  void setBigDecimal(String name, BigDecimal value) throws SQLException;

  /**
   * @see PreparedStatement#setBinaryStream(int, InputStream)
   * @see CallableStatement#setBinaryStream(String, InputStream)
   * @see ResultSet#updateBinaryStream(String, InputStream)
   * @see javax.sql.RowSet#setBinaryStream(String, InputStream)
   */
  void setBinaryStream(String name, InputStream value) throws SQLException;

  /**
   * @see PreparedStatement#setBinaryStream(int, InputStream, int)
   * @see CallableStatement#setBinaryStream(String, InputStream, int)
   * @see ResultSet#updateBinaryStream(int, InputStream, int)
   * @see javax.sql.RowSet#setBinaryStream(String, InputStream, int)
   */
  void setBinaryStream(String name, InputStream value, int length) throws SQLException;

  /**
   * @see PreparedStatement#setBinaryStream(int, InputStream, long)
   * @see CallableStatement#setBinaryStream(String, InputStream, long)
   * @see ResultSet#updateBinaryStream(int, InputStream, long)
   * @see javax.sql.RowSet#setBinaryStream(String, InputStream, int)
   */
  void setBinaryStream(String name, InputStream value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setBlob(int, Blob)
   * @see CallableStatement#setBlob(String, Blob)
   * @see ResultSet#updateBlob(String, Blob)
   * @see javax.sql.RowSet#setBlob(String, Blob)
   */
  void setBlob(String name, Blob value) throws SQLException;

  /**
   * @see PreparedStatement#setBlob(int, InputStream)
   * @see CallableStatement#setBlob(String, InputStream)
   * @see ResultSet#updateBlob(String, InputStream)
   * @see javax.sql.RowSet#setBlob(String, InputStream)
   */
  void setBlob(String name, InputStream value) throws SQLException;

  /**
   * @see PreparedStatement#setBlob(int, InputStream, long)
   * @see CallableStatement#setBlob(String, InputStream, long)
   * @see ResultSet#updateBlob(String, InputStream, long)
   * @see javax.sql.RowSet#setBlob(String, InputStream, long)
   */
  void setBlob(String name, InputStream value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setBoolean(int, boolean)
   * @see CallableStatement#setBoolean(String, boolean)
   * @see ResultSet#updateBoolean(int, boolean)
   * @see javax.sql.RowSet#setBoolean(String, boolean)
   */
  void setBoolean(String name, boolean value) throws SQLException;

  /**
   * @see PreparedStatement#setByte(int, byte)
   * @see CallableStatement#setByte(String, byte)
   * @see ResultSet#updateByte(String, byte)
   * @see javax.sql.RowSet#setByte(String, byte)
   */
  void setByte(String name, byte value) throws SQLException;

  /**
   * @see PreparedStatement#setBytes(int, byte[])
   * @see CallableStatement#setBytes(String, byte[])
   * @see ResultSet#updateBytes(String, byte[])
   * @see javax.sql.RowSet#setBytes(String, byte[])
   */
  void setBytes(String name, byte[] value) throws SQLException;

  /**
   * @see PreparedStatement#setCharacterStream(int, Reader)
   * @see CallableStatement#setCharacterStream(String, Reader)
   * @see ResultSet#updateCharacterStream(String, Reader)
   * @see javax.sql.RowSet#setCharacterStream(String, Reader)
   */
  void setCharacterStream(String name, Reader value) throws SQLException;

  /**
   * @see PreparedStatement#setCharacterStream(int, Reader, int)
   * @see CallableStatement#setCharacterStream(String, Reader, int)
   * @see ResultSet#updateCharacterStream(String, Reader, int)
   * @see javax.sql.RowSet#setCharacterStream(String, Reader, int)
   */
  void setCharacterStream(String name, Reader value, int length) throws SQLException;

  /**
   * @see PreparedStatement#setCharacterStream(int, Reader, long)
   * @see CallableStatement#setCharacterStream(String, Reader, long)
   * @see ResultSet#updateCharacterStream(String, Reader, long)
   * @see javax.sql.RowSet#setCharacterStream(String, Reader, int)
   */
  void setCharacterStream(String name, Reader value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setClob(int, Clob)
   * @see CallableStatement#setClob(String, Clob)
   * @see ResultSet#updateClob(String, Clob)
   * @see javax.sql.RowSet#setClob(String, Clob)
   */
  void setClob(String name, Clob value) throws SQLException;

  /**
   * @see PreparedStatement#setClob(int, Reader)
   * @see CallableStatement#setClob(String, Reader)
   * @see ResultSet#updateClob(String, Reader)
   * @see javax.sql.RowSet#setClob(String, Reader)
   */
  void setClob(String name, Reader value) throws SQLException;

  /**
   * @see PreparedStatement#setClob(int, Reader, long)
   * @see CallableStatement#setClob(String, Reader, long)
   * @see ResultSet#updateClob(String, Reader, long)
   * @see javax.sql.RowSet#setClob(String, Reader, long)
   */
  void setClob(String name, Reader value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setDate(int, Date)
   * @see CallableStatement#setDate(String, Date)
   * @see ResultSet#updateDate(String, Date)
   * @see javax.sql.RowSet#setDate(String, Date)
   */
  void setDate(String name, Date value) throws SQLException;

  /**
   * @see PreparedStatement#setDate(int, Date, Calendar)
   * @see CallableStatement#setDate(String, Date, Calendar)
   * @see ResultSet#updateDate(String, Date)
   * @see javax.sql.RowSet#setDate(String, Date, Calendar)
   */
  void setDate(String name, Date value, Calendar cal) throws SQLException;

  /**
   * @see PreparedStatement#setDouble(int, double)
   * @see CallableStatement#setDouble(String, double)
   * @see ResultSet#updateDouble(String, double)
   * @see javax.sql.RowSet#setDouble(String, double)
   */
  void setDouble(String name, double value) throws SQLException;

  /**
   * @see PreparedStatement#setFloat(int, float)
   * @see CallableStatement#setFloat(String, float)
   * @see ResultSet#updateFloat(String, float)
   * @see javax.sql.RowSet#setFloat(String, float)
   */
  void setFloat(String name, float value) throws SQLException;

  /**
   * @see PreparedStatement#setInt(int, int)
   * @see CallableStatement#setInt(String, int)
   * @see ResultSet#updateInt(String, int)
   * @see javax.sql.RowSet#setInt(String, int)
   */
  void setInt(String name, int value) throws SQLException;

  /**
   * @see PreparedStatement#setLong(int, long)
   * @see CallableStatement#setLong(String, long)
   * @see ResultSet#updateLong(String, long)
   * @see javax.sql.RowSet#setLong(String, long)
   */
  void setLong(String name, long value) throws SQLException;

  /**
   * @see PreparedStatement#setNCharacterStream(int, Reader)
   * @see CallableStatement#setNCharacterStream(String, Reader)
   * @see ResultSet#updateNCharacterStream(String, Reader)
   * @see javax.sql.RowSet#setNCharacterStream(String, Reader)
   */
  void setNCharacterStream(String name, Reader value) throws SQLException;

  /**
   * @see PreparedStatement#setNCharacterStream(int, Reader, long)
   * @see CallableStatement#setNCharacterStream(String, Reader, long)
   * @see ResultSet#updateNCharacterStream(String, Reader, long)
   * @see javax.sql.RowSet#setNCharacterStream(String, Reader, long)
   */
  void setNCharacterStream(String name, Reader value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setNClob(int, NClob)
   * @see CallableStatement#setNClob(String, NClob)
   * @see ResultSet#updateNClob(String, NClob)
   * @see javax.sql.RowSet#setNClob(String, NClob)
   */
  void setNClob(String name, NClob value) throws SQLException;

  /**
   * @see PreparedStatement#setNClob(int, Reader)
   * @see CallableStatement#setNClob(String, Reader)
   * @see ResultSet#updateNClob(String, Reader)
   * @see javax.sql.RowSet#setNClob(String, Reader)
   */
  void setNClob(String name, Reader value) throws SQLException;

  /**
   * @see PreparedStatement#setNClob(int, Reader, long)
   * @see CallableStatement#setNClob(String, Reader, long)
   * @see ResultSet#updateNClob(String, Reader, long)
   * @see javax.sql.RowSet#setNClob(String, Reader, long)
   */
  void setNClob(String name, Reader value, long length) throws SQLException;

  /**
   * @see PreparedStatement#setNString(int, String)
   * @see CallableStatement#setNString(String, String)
   * @see ResultSet#updateNString(String, String)
   * @see javax.sql.RowSet#setNString(String, String)
   */
  void setNString(String name, String value) throws SQLException;

  /**
   * @see PreparedStatement#setNull(int, int)
   * @see CallableStatement#setNull(String, int)
   * @see ResultSet#updateNull(int)
   * @see javax.sql.RowSet#setNull(String, int)
   */
  void setNull(String name, int sqlType) throws SQLException;

  /**
   * @see PreparedStatement#setNull(int, int, String)
   * @see CallableStatement#setNull(String, int, String)
   * @see ResultSet#updateNull(int)
   * @see javax.sql.RowSet#setNull(String, int, String)
   * @see #setNull(int, int, String)
   */
  void setNull(String name, int sqlType, String typeName) throws SQLException;

  /**
   * @see PreparedStatement#setObject(int, Object)
   * @see CallableStatement#setObject(String, Object)
   * @see ResultSet#updateObject(String, Object)
   * @see javax.sql.RowSet#setObject(String, Object)
   */
  void setObject(String name, Object value) throws SQLException;

  /**
   * @see PreparedStatement#setObject(int, Object, int)
   * @see CallableStatement#setObject(String, Object, int)
   * @see ResultSet#updateObject(String, Object, int)
   * @see javax.sql.RowSet#setObject(String, Object, int)
   */
  void setObject(String name, Object value, int targetSqlType) throws SQLException;

  /**
   * @see PreparedStatement#setObject(int, Object, int, int)
   * @see CallableStatement#setObject(int, Object, int, int)
   * @see ResultSet#updateObject(int, Object, int)
   * @see javax.sql.RowSet#setObject(int, Object, int, int)
   * @see #setObject(int, Object, int, int)
   */
  void setObject(String name, Object value, int targetSqlType, int scale) throws SQLException;

  /**
   * @see PreparedStatement#setObject(int, Object, SQLType)
   * @see CallableStatement#setObject(String, Object, SQLType)
   * @see ResultSet#updateObject(String, Object, SQLType)
   * @see javax.sql.RowSet#setObject(String, Object, int)
   */
  void setObject(String name, Object value, SQLType targetSqlType) throws SQLException;

  /**
   * @see PreparedStatement#setObject(int, Object, SQLType, int)
   * @see CallableStatement#setObject(int, Object, SQLType, int)
   * @see ResultSet#updateObject(int, Object, SQLType, int)
   * @see javax.sql.RowSet#setObject(int, Object, int, int)
   * @see #setObject(int, Object, int, int)
   */
  void setObject(String name, Object value, SQLType targetSqlType, int scaleOrLength) throws SQLException;

  /**
   * @see PreparedStatement#setRowId(int, RowId)
   * @see CallableStatement#setRowId(String, RowId)
   * @see ResultSet#updateRowId(String, RowId)
   * @see javax.sql.RowSet#setRowId(String, RowId)
   */
  void setRowId(String name, RowId value) throws SQLException;

  /**
   * @see PreparedStatement#setShort(int, short)
   * @see CallableStatement#setShort(String, short)
   * @see ResultSet#updateShort(String, short)
   * @see javax.sql.RowSet#setShort(String, short)
   */
  void setShort(String name, short value) throws SQLException;

  /**
   * @see PreparedStatement#setSQLXML(int, SQLXML)
   * @see CallableStatement#setSQLXML(String, SQLXML)
   * @see ResultSet#updateSQLXML(String, SQLXML)
   * @see javax.sql.RowSet#setSQLXML(String, SQLXML)
   */
  void setSQLXML(String name, SQLXML value) throws SQLException;

  /**
   * @see PreparedStatement#setString(int, String)
   * @see CallableStatement#setString(String, String)
   * @see ResultSet#updateString(String, String)
   * @see javax.sql.RowSet#setString(String, String)
   */
  void setString(String name, String value) throws SQLException;

  /**
   * @see PreparedStatement#setTime(int, Time)
   * @see CallableStatement#setTime(String, Time)
   * @see ResultSet#updateTimestamp(String, Timestamp)
   * @see javax.sql.RowSet#setTime(String, Time)
   */
  void setTime(String name, Time value) throws SQLException;

  /**
   * @see PreparedStatement#setTime(int, Time, Calendar)
   * @see CallableStatement#setTime(String, Time, Calendar)
   * @see ResultSet#updateTime(String, Time)
   * @see javax.sql.RowSet#setTime(String, Time, Calendar)
   */
  void setTime(String name, Time value, Calendar cal) throws SQLException;

  /**
   * @see PreparedStatement#setTimestamp(int, Timestamp)
   * @see CallableStatement#setTimestamp(String, Timestamp)
   * @see ResultSet#updateTimestamp(String, Timestamp)
   * @see javax.sql.RowSet#setTimestamp(String, Timestamp)
   */
  void setTimestamp(String name, Timestamp value) throws SQLException;

  /**
   * @see PreparedStatement#setTimestamp(int, Timestamp, Calendar)
   * @see CallableStatement#setTimestamp(String, Timestamp, Calendar)
   * @see ResultSet#updateTimestamp(String, Timestamp)
   * @see javax.sql.RowSet#setTimestamp(String, Timestamp, Calendar)
   */
  void setTimestamp(String name, Timestamp value, Calendar cal) throws SQLException;

  /**
   * @see PreparedStatement#setURL(int, URL)
   * @see CallableStatement#setURL(String, URL)
   * @see ResultSet#updateObject(String, Object)
   * @see javax.sql.RowSet#setURL(int, URL)
   */
  void setURL(String name, URL value) throws SQLException;

}
