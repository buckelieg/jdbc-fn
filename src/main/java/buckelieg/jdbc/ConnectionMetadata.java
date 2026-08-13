package buckelieg.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class ConnectionMetadata {

  public final AtomicLong lastTimeUsed = new AtomicLong();
  public final AtomicBoolean isBusy = new AtomicBoolean();
  public final int holdability;
  public final boolean autoCommit;
  public final boolean readOnly;
  public final int isolationLevel;

  private ConnectionMetadata(
		  long lastTimeUsed,
		  int holdability,
		  boolean autoCommit,
		  boolean readOnly,
		  int isolationLevel) {
	this.holdability = holdability;
	this.autoCommit = autoCommit;
	this.readOnly = readOnly;
	this.isolationLevel = isolationLevel;
	this.lastTimeUsed.set(lastTimeUsed);
  }

  private static ConnectionMetadata of(int holdability, boolean autoCommit, boolean readOnly, int isolationLevel) {
	return new ConnectionMetadata(System.currentTimeMillis(), holdability, autoCommit, readOnly, isolationLevel);
  }

  public static ConnectionMetadata of(Connection connection) throws SQLException {
	return of(connection.getHoldability(), connection.getAutoCommit(), connection.isReadOnly(), connection.getTransactionIsolation());
  }

}
