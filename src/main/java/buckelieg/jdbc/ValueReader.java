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
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;

/**
 * An abstraction for "read" operations on either {@linkplain ResultSet} or appropriate {@linkplain Statement}s
 * <br/>This is needed to "normalize" raw JDBC API with common methods
 */
public interface ValueReader {

  /**
   * Returns a metadata for this writer object
   *
   * @return a {@linkplain Metadata} for this ValueWriter
   */
  @Nonnull
  Metadata meta();

  /**
   * @see ResultSet#getArray(int)
   * @see CallableStatement#getArray(int)
   */
  Array getArray(int index) throws SQLException;

  /**
   * @see ResultSet#getAsciiStream(int)
   * @see CallableStatement#getBytes(int)
   */
  InputStream getAsciiStream(int index) throws SQLException;

  /**
   * @see ResultSet#getBigDecimal(int)
   * @see CallableStatement#getBigDecimal(int)
   */
  BigDecimal getBigDecimal(int index) throws SQLException;

  /**
   * @see ResultSet#getBigDecimal(int, int)
   * @see CallableStatement#getBigDecimal(int, int)
   */
  @Deprecated
  BigDecimal getBigDecimal(int index, int scale) throws SQLException;

  /**
   * @see ResultSet#getBigDecimal(int)
   * @see CallableStatement#getBytes(int)
   * @see java.io.ByteArrayInputStream
   */
  InputStream getBinaryStream(int index) throws SQLException;

  /**
   * @see ResultSet#getBlob(int)
   * @see CallableStatement#getBlob(int)
   */
  Blob getBlob(int index) throws SQLException;

  /**
   * @see ResultSet#getBoolean(int)
   * @see CallableStatement#getBoolean(int)
   */
  boolean getBoolean(int index) throws SQLException;

  /**
   * @see ResultSet#getByte(int)
   * @see CallableStatement#getByte(int)
   */
  byte getByte(int index) throws SQLException;

  /**
   * @see ResultSet#getBytes(int)
   * @see CallableStatement#getBytes(int)
   */
  byte[] getBytes(int index) throws SQLException;

  /**
   * @see ResultSet#getCharacterStream(int)
   * @see CallableStatement#getCharacterStream(int)
   */
  Reader getCharacterStream(int index) throws SQLException;

  /**
   * @see ResultSet#getClob(int)
   * @see CallableStatement#getClob(int)
   */
  Clob getClob(int index) throws SQLException;

  /**
   * @see ResultSet#getDate(int)
   * @see CallableStatement#getDate(int)
   */
  Date getDate(int index) throws SQLException;

  /**
   * @see ResultSet#getDate(int, Calendar)
   * @see CallableStatement#getDate(int, Calendar)
   */
  Date getDate(int index, Calendar cal) throws SQLException;

  /**
   * @see ResultSet#getDouble(int)
   * @see CallableStatement#getDouble(int)
   */
  double getDouble(int index) throws SQLException;

  /**
   * @see ResultSet#getFloat(int)
   * @see CallableStatement#getFloat(int)
   */
  float getFloat(int index) throws SQLException;

  /**
   * @see ResultSet#getInt(int)
   * @see CallableStatement#getInt(int)
   */
  int getInt(int index) throws SQLException;

  /**
   * @see ResultSet#getLong(int)
   * @see CallableStatement#getLong(int)
   */
  long getLong(int index) throws SQLException;

  /**
   * @see ResultSet#getNCharacterStream(int)
   * @see CallableStatement#getNCharacterStream(int)
   */
  Reader getNCharacterStream(int index) throws SQLException;

  /**
   * @see ResultSet#getNClob(int)
   * @see CallableStatement#getNClob(int)
   */
  NClob getNClob(int index) throws SQLException;

  /**
   * @see ResultSet#getNString(int)
   * @see CallableStatement#getNString(int)
   */
  String getNString(int index) throws SQLException;

  /**
   * @see ResultSet#getObject(int)
   * @see CallableStatement#getObject(int)
   */
  Object getObject(int index) throws SQLException;

  /**
   * @see ResultSet#getObject(int, Class)
   * @see CallableStatement#getObject(int, Class)
   */
  <T> T getObject(int index, Class<T> type) throws SQLException;

  /**
   * @see ResultSet#getObject(int, java.util.Map)
   * @see CallableStatement#getObject(int, java.util.Map)
   */
  Object getObject(int index, java.util.Map<String, Class<?>> map) throws SQLException;

  /**
   * @see ResultSet#getRef(int)
   * @see CallableStatement#getRef(int)
   */
  Ref getRef(int index) throws SQLException;

  /**
   * @see ResultSet#getRowId(int)
   * @see CallableStatement#getRowId(int)
   */
  RowId getRowId(int index) throws SQLException;

  /**
   * @see ResultSet#getShort(int)
   * @see CallableStatement#getShort(int)
   */
  short getShort(int index) throws SQLException;

  /**
   * @see ResultSet#getSQLXML(int)
   * @see CallableStatement#getSQLXML(int)
   */
  SQLXML getSQLXML(int index) throws SQLException;

  /**
   * @see ResultSet#getString(int)
   * @see CallableStatement#getString(int)
   */
  String getString(int index) throws SQLException;

  /**
   * @see ResultSet#getTime(int)
   * @see CallableStatement#getTime(int)
   */
  Time getTime(int index) throws SQLException;

  /**
   * @see ResultSet#getTime(int, Calendar)
   * @see CallableStatement#getTime(int, Calendar)
   */
  Time getTime(int index, Calendar cal) throws SQLException;

  /**
   * @see ResultSet#getTimestamp(int)
   * @see CallableStatement#getTimestamp(int)
   */
  Timestamp getTimestamp(int index) throws SQLException;

  /**
   * @see ResultSet#getTimestamp(int, Calendar)
   * @see CallableStatement#getTimestamp(int, Calendar)
   */
  Timestamp getTimestamp(int index, Calendar cal) throws SQLException;

  /**
   * @see ResultSet#getUnicodeStream(int)
   * @see CallableStatement#getBlob(int)
   * @see Blob#getBinaryStream()
   */
  @Deprecated
  InputStream getUnicodeStream(int index) throws SQLException;

  /**
   * @see ResultSet#getURL(int)
   * @see CallableStatement#getURL(int)
   */
  URL getURL(int index) throws SQLException;

  /**
   * @see ResultSet#getArray(String)
   * @see CallableStatement#getArray(String)
   */
  Array getArray(String name) throws SQLException;

  /**
   * @see ResultSet#getAsciiStream(String)
   * @see CallableStatement#getBytes(String)
   */
  InputStream getAsciiStream(String name) throws SQLException;

  /**
   * @see ResultSet#getBigDecimal(String)
   * @see CallableStatement#getBigDecimal(String)
   */
  BigDecimal getBigDecimal(String name) throws SQLException;

  /**
   * @see ResultSet#getBigDecimal(String, int)
   * @see CallableStatement#getBigDecimal(int, int)
   * @see CallableStatement#getBigDecimal(String)
   */
  @Deprecated
  BigDecimal getBigDecimal(String name, int scale) throws SQLException;

  /**
   * @see ResultSet#getBinaryStream(String)
   * @see CallableStatement#getBytes(String)
   * @see java.io.ByteArrayInputStream
   */
  InputStream getBinaryStream(String name) throws SQLException;

  /**
   * @see ResultSet#getBlob(String)
   * @see CallableStatement#getBlob(String)
   */
  Blob getBlob(String name) throws SQLException;

  /**
   * @see ResultSet#getBoolean(String)
   * @see CallableStatement#getBoolean(String)
   */
  boolean getBoolean(String name) throws SQLException;

  /**
   * @see ResultSet#getByte(String)
   * @see CallableStatement#getByte(String)
   */
  byte getByte(String name) throws SQLException;

  /**
   * @see ResultSet#getBytes(String)
   * @see CallableStatement#getBytes(String)
   */
  byte[] getBytes(String name) throws SQLException;

  /**
   * @see ResultSet#getCharacterStream(String)
   * @see CallableStatement#getCharacterStream(String)
   */
  Reader getCharacterStream(String name) throws SQLException;

  /**
   * @see ResultSet#getClob(String)
   * @see CallableStatement#getClob(String)
   */
  Clob getClob(String name) throws SQLException;

  /**
   * @see ResultSet#getDate(String)
   * @see CallableStatement#getDate(String)
   */
  Date getDate(String name) throws SQLException;

  /**
   * @see ResultSet#getDate(String, Calendar)
   * @see CallableStatement#getDate(String, Calendar)
   */
  Date getDate(String name, Calendar cal) throws SQLException;

  /**
   * @see ResultSet#getDouble(String)
   * @see CallableStatement#getDouble(String)
   */
  double getDouble(String name) throws SQLException;

  /**
   * @see ResultSet#getFloat(String)
   * @see CallableStatement#getFloat(String)
   */
  float getFloat(String name) throws SQLException;

  /**
   * @see ResultSet#getInt(String)
   * @see CallableStatement#getInt(String)
   */
  int getInt(String name) throws SQLException;

  /**
   * @see ResultSet#getLong(String)
   * @see CallableStatement#getLong(String)
   */
  long getLong(String name) throws SQLException;

  /**
   * @see ResultSet#getNCharacterStream(String)
   * @see CallableStatement#getNCharacterStream(String)
   */
  Reader getNCharacterStream(String name) throws SQLException;

  /**
   * @see ResultSet#getNClob(String)
   * @see CallableStatement#getNClob(String)
   */
  NClob getNClob(String name) throws SQLException;

  /**
   * @see ResultSet#getNString(String)
   * @see CallableStatement#getNString(String)
   */
  String getNString(String name) throws SQLException;

  /**
   * @see ResultSet#getObject(String)
   * @see CallableStatement#getObject(String)
   */
  Object getObject(String name) throws SQLException;

  /**
   * @see ResultSet#getObject(String, Class)
   * @see CallableStatement#getObject(String, Class)
   */
  <T> T getObject(String name, Class<T> type) throws SQLException;

  /**
   * @see ResultSet#getObject(String, java.util.Map)
   * @see CallableStatement#getObject(String, java.util.Map)
   */
  Object getObject(String name, java.util.Map<String, Class<?>> map) throws SQLException;

  /**
   * @see ResultSet#getRef(String)
   * @see CallableStatement#getRef(String)
   */
  Ref getRef(String name) throws SQLException;

  /**
   * @see ResultSet#getRowId(String)
   * @see CallableStatement#getRowId(String)
   */
  RowId getRowId(String name) throws SQLException;

  /**
   * @see ResultSet#getShort(String)
   * @see CallableStatement#getShort(String)
   */
  short getShort(String name) throws SQLException;

  /**
   * @see ResultSet#getSQLXML(String)
   * @see CallableStatement#getSQLXML(String)
   */
  SQLXML getSQLXML(String name) throws SQLException;

  /**
   * @see ResultSet#getString(String)
   * @see CallableStatement#getString(String)
   */
  String getString(String name) throws SQLException;

  /**
   * @see ResultSet#getTime(String)
   * @see CallableStatement#getTime(String)
   */
  Time getTime(String name) throws SQLException;

  /**
   * @see ResultSet#getTime(String, Calendar)
   * @see CallableStatement#getTime(String, Calendar)
   */
  Time getTime(String name, Calendar cal) throws SQLException;

  /**
   * @see ResultSet#getTimestamp(String)
   * @see CallableStatement#getTimestamp(String)
   */
  Timestamp getTimestamp(String name) throws SQLException;

  /**
   * @see ResultSet#getTimestamp(String, Calendar)
   * @see CallableStatement#getTimestamp(String, Calendar)
   */
  Timestamp getTimestamp(String name, Calendar cal) throws SQLException;

  /**
   * @see ResultSet#getUnicodeStream(String)
   * @see CallableStatement#getBlob(String)
   * @see Blob#getBinaryStream()
   */
  @Deprecated
  InputStream getUnicodeStream(String name) throws SQLException;

  /**
   * @see ResultSet#getURL(String)
   * @see CallableStatement#getURL(String)
   */
  URL getURL(String name) throws SQLException;

}
