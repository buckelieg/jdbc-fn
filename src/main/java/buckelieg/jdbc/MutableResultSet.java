package buckelieg.jdbc;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.*;

final class MutableResultSet extends ImmutableResultSet {

    boolean updated = false;

    MutableResultSet(@Nonnull ResultSet delegate) {
        super(delegate);
    }

    @Override
    public void updateNull(int columnIndex) throws SQLException {
        delegate.updateNull(columnIndex);
        updated = true;
    }

    @Override
    public void updateBoolean(int columnIndex, boolean x) throws SQLException {
        delegate.updateBoolean(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateByte(int columnIndex, byte x) throws SQLException {
        delegate.updateByte(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateShort(int columnIndex, short x) throws SQLException {
        delegate.updateShort(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateInt(int columnIndex, int x) throws SQLException {
        delegate.updateInt(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateLong(int columnIndex, long x) throws SQLException {
        delegate.updateLong(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateFloat(int columnIndex, float x) throws SQLException {
        delegate.updateFloat(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateDouble(int columnIndex, double x) throws SQLException {
        delegate.updateDouble(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
        delegate.updateBigDecimal(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateString(int columnIndex, String x) throws SQLException {
        delegate.updateString(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateBytes(int columnIndex, byte[] x) throws SQLException {
        delegate.updateBytes(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateDate(int columnIndex, Date x) throws SQLException {
        delegate.updateDate(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateTime(int columnIndex, Time x) throws SQLException {
        delegate.updateTime(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
        delegate.updateTimestamp(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
        delegate.updateAsciiStream(columnIndex, x, length);
        updated = true;
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
        delegate.updateBinaryStream(columnIndex, x, length);
        updated = true;
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
        delegate.updateCharacterStream(columnIndex, x, length);
        updated = true;
    }

    @Override
    public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
        delegate.updateObject(columnIndex, x, scaleOrLength);
        updated = true;
    }

    @Override
    public void updateObject(int columnIndex, Object x) throws SQLException {
        delegate.updateObject(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateNull(String columnLabel) throws SQLException {
        delegate.updateNull(columnLabel);
        updated = true;
    }

    @Override
    public void updateBoolean(String columnLabel, boolean x) throws SQLException {
        delegate.updateBoolean(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateByte(String columnLabel, byte x) throws SQLException {
        delegate.updateByte(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateShort(String columnLabel, short x) throws SQLException {
        delegate.updateShort(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateInt(String columnLabel, int x) throws SQLException {
        delegate.updateInt(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateLong(String columnLabel, long x) throws SQLException {
        delegate.updateLong(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateFloat(String columnLabel, float x) throws SQLException {
        delegate.updateFloat(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateDouble(String columnLabel, double x) throws SQLException {
        delegate.updateDouble(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
        delegate.updateBigDecimal(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateString(String columnLabel, String x) throws SQLException {
        delegate.updateString(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateBytes(String columnLabel, byte[] x) throws SQLException {
        delegate.updateBytes(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateDate(String columnLabel, Date x) throws SQLException {
        delegate.updateDate(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateTime(String columnLabel, Time x) throws SQLException {
        delegate.updateTime(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
        delegate.updateTimestamp(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
        delegate.updateAsciiStream(columnLabel, x, length);
        updated = true;
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
        delegate.updateBinaryStream(columnLabel, x, length);
        updated = true;
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException {
        delegate.updateCharacterStream(columnLabel, reader, length);
        updated = true;
    }

    @Override
    public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
        delegate.updateObject(columnLabel, x, scaleOrLength);
        updated = true;
    }

    @Override
    public void updateObject(String columnLabel, Object x) throws SQLException {
        delegate.updateObject(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateRef(int columnIndex, Ref x) throws SQLException {
        delegate.updateRef(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateRef(String columnLabel, Ref x) throws SQLException {
        delegate.updateRef(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateBlob(int columnIndex, Blob x) throws SQLException {
        delegate.updateBlob(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateBlob(String columnLabel, Blob x) throws SQLException {
        delegate.updateBlob(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateClob(int columnIndex, Clob x) throws SQLException {
        delegate.updateClob(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateClob(String columnLabel, Clob x) throws SQLException {
        delegate.updateClob(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateArray(int columnIndex, Array x) throws SQLException {
        delegate.updateArray(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateArray(String columnLabel, Array x) throws SQLException {
        delegate.updateArray(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateRowId(int columnIndex, RowId x) throws SQLException {
        delegate.updateRowId(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateRowId(String columnLabel, RowId x) throws SQLException {
        delegate.updateRowId(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateNString(int columnIndex, String nString) throws SQLException {
        delegate.updateNString(columnIndex, nString);
        updated = true;
    }

    @Override
    public void updateNString(String columnLabel, String nString) throws SQLException {
        delegate.updateNString(columnLabel, nString);
        updated = true;
    }

    @Override
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
        delegate.updateNClob(columnIndex, nClob);
        updated = true;
    }

    @Override
    public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
        delegate.updateNClob(columnLabel, nClob);
        updated = true;
    }

    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
        delegate.updateSQLXML(columnIndex, xmlObject);
        updated = true;
    }

    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
        delegate.updateSQLXML(columnLabel, xmlObject);
        updated = true;
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        delegate.updateNCharacterStream(columnIndex, x, length);
        updated = true;
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        delegate.updateNCharacterStream(columnLabel, reader, length);
        updated = true;
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
        delegate.updateAsciiStream(columnIndex, x, length);
        updated = true;
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
        delegate.updateBinaryStream(columnIndex, x, length);
        updated = true;
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        delegate.updateCharacterStream(columnIndex, x, length);
        updated = true;
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
        delegate.updateAsciiStream(columnLabel, x, length);
        updated = true;
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
        delegate.updateBinaryStream(columnLabel, x, length);
        updated = true;
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        delegate.updateCharacterStream(columnLabel, reader, length);
        updated = true;
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
        delegate.updateBlob(columnIndex, inputStream, length);
        updated = true;
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
        delegate.updateBlob(columnLabel, inputStream, length);
        updated = true;
    }

    @Override
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
        delegate.updateClob(columnIndex, reader, length);
        updated = true;
    }

    @Override
    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
        delegate.updateClob(columnLabel, reader, length);
        updated = true;
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
        delegate.updateNClob(columnIndex, reader, length);
        updated = true;
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
        delegate.updateNClob(columnLabel, reader, length);
        updated = true;
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
        delegate.updateNCharacterStream(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
        delegate.updateNCharacterStream(columnLabel, reader);
        updated = true;
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
        delegate.updateAsciiStream(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
        delegate.updateBinaryStream(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
        delegate.updateCharacterStream(columnIndex, x);
        updated = true;
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
        delegate.updateAsciiStream(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
        delegate.updateBinaryStream(columnLabel, x);
        updated = true;
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
        delegate.updateCharacterStream(columnLabel, reader);
        updated = true;
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
        delegate.updateBlob(columnIndex, inputStream);
        updated = true;
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
        delegate.updateBlob(columnLabel, inputStream);
        updated = true;
    }

    @Override
    public void updateClob(int columnIndex, Reader reader) throws SQLException {
        delegate.updateClob(columnIndex, reader);
        updated = true;
    }

    @Override
    public void updateClob(String columnLabel, Reader reader) throws SQLException {
        delegate.updateClob(columnLabel, reader);
        updated = true;
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader) throws SQLException {
        delegate.updateNClob(columnIndex, reader);
        updated = true;
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader) throws SQLException {
        delegate.updateNClob(columnLabel, reader);
        updated = true;
    }

}
