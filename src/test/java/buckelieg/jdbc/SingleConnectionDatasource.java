package buckelieg.jdbc;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

final class SingleConnectionDatasource implements DataSource {

  private final Connection connection;

  SingleConnectionDatasource(Connection connection) {
	this.connection = connection;
  }

  @Override
  public Connection getConnection() throws SQLException {
	return connection;
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
	return connection;
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
	return connection.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
	return connection.isWrapperFor(iface);
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
	return null;
  }

  @Override
  public void setLogWriter(PrintWriter out) throws SQLException {

  }

  @Override
  public void setLoginTimeout(int seconds) throws SQLException {

  }

  @Override
  public int getLoginTimeout() throws SQLException {
	return 0;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
	return null;
  }
}
