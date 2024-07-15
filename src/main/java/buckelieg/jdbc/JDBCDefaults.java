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
import buckelieg.jdbc.fn.TryFunction;
import buckelieg.jdbc.fn.TryTriConsumer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.*;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static buckelieg.jdbc.Utils.entry;
import static java.lang.Math.max;
import static java.sql.JDBCType.*;
import static java.util.stream.Collectors.joining;

enum JDBCDefaults {

  ;

  private static final int DEFAULT_BUFFER_SIZE = 4096; // TODO make customizable

  private static final Map<SQLType, TryBiFunction<ValueReader, Integer, ?, SQLException>> defaultReaders = new HashMap<>();

  private static final Map<SQLType, TryTriConsumer<ValueWriter, Integer, ?, SQLException>> defaultWriters = new HashMap<>();

  static TryFunction<ValueReader, Map<String, Object>, SQLException> defaultMapper() {
	return new DefaultValueMapper();
  }

  static Map<String, Object> defaultMapper(ValueReader reader) throws SQLException {
	Metadata meta = reader.meta();
	Map<String, Object> result = new HashMap<>(meta.count());
	reader.meta().forEachColumn(index -> result.put(meta.getName(index), reader(meta.getSQLType(index)).apply(reader, index)));
	return result;
  }


  static final class DefaultValueMapper implements TryFunction<ValueReader, Map<String, Object>, SQLException> {

	private List<Map.Entry<Map.Entry<String, Integer>, TryBiFunction<ValueReader, Integer, ?, SQLException>>> colReaders;
	private final AtomicReference<TryFunction<ValueReader, Map<String, Object>, SQLException>> mapper = new AtomicReference<>();

	@Override
	public Map<String, Object> apply(ValueReader valueReader) throws SQLException {
	  return mapper.updateAndGet(instance -> {
		if (null == instance) {
		  Metadata meta = valueReader.meta();
		  int columnCount = meta.count();
		  colReaders = new ArrayList<>(columnCount);
		  for (int col = 1; col <= columnCount; col++)
			colReaders.add(entry(entry(meta.getLabel(col), col), reader(meta.getSQLType(col))));
		  instance = getter -> {
			Map<String, Object> result = new LinkedHashMap<>(columnCount);
			for (Map.Entry<Map.Entry<String, Integer>, TryBiFunction<ValueReader, Integer, ?, SQLException>> e : colReaders)
			  result.put(e.getKey().getKey(), e.getValue().apply(getter, e.getKey().getValue()));
			return result;
		  };
		}
		return instance;
	  }).apply(valueReader);
	}
  }

  static {
	defaultReaders.put(ARRAY, ValueReader::getArray);
	defaultReaders.put(BINARY, ValueReader::getBytes);
	defaultReaders.put(BIGINT, ValueReader::getLong);
	defaultReaders.put(BLOB, longVarBinaryReader(DEFAULT_BUFFER_SIZE, (rs, index) -> {
	  Blob blob = rs.getBlob(index);
	  return null == blob ? null : blob.getBinaryStream();
	}));
	defaultReaders.put(BIT, ValueReader::getBoolean);
	defaultReaders.put(CHAR, ValueReader::getString);
	defaultReaders.put(CLOB, longVarCharReader(clobReader(ValueReader::getClob)));
	defaultReaders.put(DATE, ValueReader::getDate);
	defaultReaders.put(DECIMAL, ValueReader::getBigDecimal);
	defaultReaders.put(DOUBLE, ValueReader::getDouble);
	defaultReaders.put(FLOAT, ValueReader::getDouble);
	defaultReaders.put(INTEGER, ValueReader::getInt);
	defaultReaders.put(JAVA_OBJECT, ValueReader::getObject);
	defaultReaders.put(LONGVARBINARY, longVarBinaryReader(DEFAULT_BUFFER_SIZE, ValueReader::getBinaryStream));
	defaultReaders.put(LONGVARCHAR, longVarCharReader(ValueReader::getCharacterStream));
	defaultReaders.put(LONGNVARCHAR, longVarCharReader(ValueReader::getNCharacterStream));
	defaultReaders.put(NCLOB, longVarCharReader(clobReader(ValueReader::getNClob)));
	defaultReaders.put(NUMERIC, ValueReader::getBigDecimal);
	defaultReaders.put(OTHER, ValueReader::getObject);
	defaultReaders.put(REAL, ValueReader::getFloat);
	defaultReaders.put(SMALLINT, ValueReader::getShort);
	defaultReaders.put(TIME, ValueReader::getTime);
	defaultReaders.put(TIME_WITH_TIMEZONE, ValueReader::getTime);
	defaultReaders.put(TIMESTAMP, ValueReader::getTimestamp);
	defaultReaders.put(TIMESTAMP_WITH_TIMEZONE, ValueReader::getTimestamp);
	defaultReaders.put(TINYINT, ValueReader::getByte);
	defaultReaders.put(VARBINARY, ValueReader::getBytes);
	defaultReaders.put(VARCHAR, ValueReader::getString);

	defaultWriters.put(ARRAY, TryTriConsumer.<ValueWriter, Integer, Array, SQLException>of(ValueWriter::setArray));
	defaultWriters.put(BIGINT, TryTriConsumer.<ValueWriter, Integer, Long, SQLException>of(ValueWriter::setLong));
	defaultWriters.put(BINARY, TryTriConsumer.<ValueWriter, Integer, byte[], SQLException>of(ValueWriter::setBytes));
	defaultWriters.put(BIT, TryTriConsumer.<ValueWriter, Integer, Boolean, SQLException>of(ValueWriter::setBoolean));
	defaultWriters.put(BLOB, TryTriConsumer.<ValueWriter, Integer, Blob, SQLException>of(ValueWriter::setBlob));
	defaultWriters.put(CHAR, TryTriConsumer.<ValueWriter, Integer, String, SQLException>of(ValueWriter::setString));
	defaultWriters.put(CLOB, TryTriConsumer.<ValueWriter, Integer, Clob, SQLException>of(ValueWriter::setClob));
	defaultWriters.put(DATE, TryTriConsumer.<ValueWriter, Integer, Date, SQLException>of(ValueWriter::setDate));
	defaultWriters.put(DECIMAL, TryTriConsumer.<ValueWriter, Integer, BigDecimal, SQLException>of(ValueWriter::setBigDecimal));
	defaultWriters.put(DOUBLE, TryTriConsumer.<ValueWriter, Integer, Double, SQLException>of(ValueWriter::setDouble));
	defaultWriters.put(FLOAT, TryTriConsumer.<ValueWriter, Integer, Double, SQLException>of(ValueWriter::setDouble));
	defaultWriters.put(INTEGER, TryTriConsumer.<ValueWriter, Integer, Integer, SQLException>of(ValueWriter::setInt));
	defaultWriters.put(JAVA_OBJECT, JDBCDefaults::setObject);
	defaultWriters.put(LONGVARBINARY, TryTriConsumer.<ValueWriter, Integer, InputStream, SQLException>of(ValueWriter::setBinaryStream));
	defaultWriters.put(LONGVARCHAR, TryTriConsumer.<ValueWriter, Integer, Reader, SQLException>of(ValueWriter::setCharacterStream));
	defaultWriters.put(LONGNVARCHAR, TryTriConsumer.<ValueWriter, Integer, Reader, SQLException>of(ValueWriter::setNCharacterStream));
	defaultWriters.put(NCLOB, TryTriConsumer.<ValueWriter, Integer, NClob, SQLException>of(ValueWriter::setNClob));
	defaultWriters.put(NUMERIC, TryTriConsumer.<ValueWriter, Integer, BigDecimal, SQLException>of(ValueWriter::setBigDecimal));
	defaultWriters.put(OTHER, JDBCDefaults::setObject);
	defaultWriters.put(REAL, TryTriConsumer.<ValueWriter, Integer, Float, SQLException>of(ValueWriter::setFloat));
	defaultWriters.put(SMALLINT, TryTriConsumer.<ValueWriter, Integer, Short, SQLException>of(ValueWriter::setShort));
	defaultWriters.put(TIME, TryTriConsumer.<ValueWriter, Integer, Time, SQLException>of(ValueWriter::setTime));
	defaultWriters.put(TIME_WITH_TIMEZONE, TryTriConsumer.<ValueWriter, Integer, Time, SQLException>of(ValueWriter::setTime));
	defaultWriters.put(TIMESTAMP, TryTriConsumer.<ValueWriter, Integer, Timestamp, SQLException>of(ValueWriter::setTimestamp));
	defaultWriters.put(TIMESTAMP_WITH_TIMEZONE, TryTriConsumer.<ValueWriter, Integer, Timestamp, SQLException>of(ValueWriter::setTimestamp));
	defaultWriters.put(TINYINT, TryTriConsumer.<ValueWriter, Integer, Byte, SQLException>of(ValueWriter::setByte));
	defaultWriters.put(VARBINARY, TryTriConsumer.<ValueWriter, Integer, byte[], SQLException>of(ValueWriter::setBytes));
	defaultWriters.put(VARCHAR, TryTriConsumer.<ValueWriter, Integer, String, SQLException>of(ValueWriter::setString));
  }

  private static TryBiFunction<ValueReader, Integer, byte[], SQLException> longVarBinaryReader(int bufferSize, TryBiFunction<ValueReader, Integer, InputStream, SQLException> binaryStreamProvider) {
	return (input, index) -> {
	  try (InputStream is = binaryStreamProvider.apply(input, index)) {
		if (null == is) return null;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buffer = new byte[max(DEFAULT_BUFFER_SIZE, bufferSize)];
		int length, offset = 0;
		while ((length = is.read(buffer)) != -1) {
		  bos.write(buffer, offset, length);
		  offset += length;
		}
		return bos.toByteArray();
	  } catch (Throwable t) {
		throw new SQLException(t);
	  }
	};
  }

  private static <C extends Clob> TryBiFunction<ValueReader, Integer, Reader, SQLException> clobReader(TryBiFunction<ValueReader, Integer, C, SQLException> binaryStreamProvider) {
	return (input, index) -> {
	  C clob = binaryStreamProvider.apply(input, index);
	  return null == clob ? null : clob.getCharacterStream();
	};
  }

  private static TryBiFunction<ValueReader, Integer, String, SQLException> longVarCharReader(TryBiFunction<ValueReader, Integer, Reader, SQLException> toReader) {
	return (input, index) -> {
	  try (Reader r = toReader.apply(input, index)) {
		return null == r ? null : new BufferedReader(r).lines().collect(joining());
	  } catch (Throwable t) {
		throw new SQLException(t);
	  }
	};
  }

  private static void setObject(ValueWriter writer, int index, Object value) throws SQLException {
	if (value == null) writer.setObject(index, null);
	else {
	  Class<?> cls = value.getClass();
	  if (Clob.class.isAssignableFrom(cls)) writer.setClob(index, (Clob) value);
	  else if (Blob.class.isAssignableFrom(cls)) writer.setBlob(index, (Blob) value);
	  else if (Time.class.isAssignableFrom(cls)) writer.setTime(index, (Time) value, Calendar.getInstance());
	  else if (Timestamp.class.isAssignableFrom(cls)) writer.setTimestamp(index, (Timestamp) value, Calendar.getInstance());
	  else if (java.sql.Date.class.isAssignableFrom(cls)) writer.setDate(index, (Date) value, Calendar.getInstance());
	  else if (java.util.Date.class.isAssignableFrom(cls)) writer.setTimestamp(index, new Timestamp(((java.util.Date) value).getTime()), Calendar.getInstance());
	  else if (Calendar.class.isAssignableFrom(cls)) writer.setTimestamp(index, new Timestamp(((Calendar) value).getTimeInMillis()));
	  else if (Instant.class.isAssignableFrom(cls)) writer.setTimestamp(index, new Timestamp(((Instant) value).toEpochMilli()), Calendar.getInstance());
	  else if (ZonedDateTime.class.isAssignableFrom(cls)) writer.setTimestamp(index, new Timestamp(((ZonedDateTime) value).toInstant().toEpochMilli()), Calendar.getInstance());
	  else writer.setObject(index, value);
	}
  }

  @SuppressWarnings("unchecked")
  static <T> TryBiFunction<ValueReader, Integer, T, SQLException> reader(SQLType type) {
	return (TryBiFunction<ValueReader, Integer, T, SQLException>) defaultReaders.getOrDefault(type, ValueReader::getObject);
  }

  @SuppressWarnings("unchecked")
  static <T> TryTriConsumer<ValueWriter, Integer, T, SQLException> writer(SQLType type) {
	return (TryTriConsumer<ValueWriter, Integer, T, SQLException>) defaultWriters.getOrDefault(type, JDBCDefaults::setObject);
  }

}
