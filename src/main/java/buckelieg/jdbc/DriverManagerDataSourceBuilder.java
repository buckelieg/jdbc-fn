package buckelieg.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

/**
 * A data source that uses {@linkplain java.sql.DriverManager} to create database connections
 */
public final class DriverManagerDataSourceBuilder {

  private static final String PARAM_USER = "user";
  private static final String PARAM_PASSWORD = "password";

  /**
   * Keepalive policy configurer for this datasource
   */
  public static final class KeepAlivePolicy {

	private final DriverManagerDataSourceBuilder builder;

	private KeepAlivePolicy(DriverManagerDataSourceBuilder builder) {
	  this.builder = builder;
	}

	/**
	 * Sets keep alive query to execute against connection to test its liveness
	 *
	 * @param testQuery a valid SQL query to run on connection
	 */
	public void setKeepAliveQuery(String testQuery) {
	  builder.keepAliveQuery = testQuery;
	}

	public void setExecutionSchedule(Duration duration) {
	  builder.keepAliveDuration = duration;
	}

  }

  private int maxConnections = Runtime.getRuntime().availableProcessors();

  private final Properties properties = new Properties();

  private String url;

  private String user;

  private String password;

  private String driverClass;

  private String keepAliveQuery;

  private Duration keepAliveDuration;

  private final AtomicBoolean driverClassLoaded = new AtomicBoolean();

  DriverManagerDataSourceBuilder() {
  }

  /**
   * Configures a {@linkplain DB} instance with an upper limit of obtained (by this {@linkplain DB} instance) connections count<br/>
   * Default value is {@linkplain Runtime#availableProcessors()}
   *
   * @param count maximum connection to obtain (values less than {@code 1} are silently ignored)
   */
  public DriverManagerDataSourceBuilder withMaxConnections(int count) {
	this.maxConnections = max(1, count);
	return this;
  }

  int getMaxConnections() {
	return maxConnections;
  }

  /**
   * Sets timeout for idle connections. Connection becomes stale (ripped out from pool) after timeout expires
   *
   * @param duration a timeout duration
   */
  public DriverManagerDataSourceBuilder withIdleConnectionTimeout(Duration duration) {
	return this;
  }

  public DriverManagerDataSourceBuilder withProperty(String key, String value) {
	properties.setProperty(key, value);
	return this;
  }

  private DriverManagerDataSourceBuilder withURL(String url) {
	if (requireNonNull(url, "Connection URL cannot be null").isEmpty()) throw new IllegalArgumentException("Connection URL cannot be empty");
	this.url = url;
	return this;
  }

  public DriverManagerDataSourceBuilder withUser(String user) {
	this.user = user;
	return this;
  }

  public DriverManagerDataSourceBuilder withPassword(String password) {
	this.password = password;
	return this;
  }

  public DriverManagerDataSourceBuilder withDriverClass(String driverClass) {
	this.driverClass = driverClass;
	return this;
  }

  /**
   * @param configurator
   * @return
   */
  public DriverManagerDataSourceBuilder withKeepAlivePolicy(Consumer<KeepAlivePolicy> configurator) {
	configurator.accept(new KeepAlivePolicy(this));
	return this;
  }

  String getKeepAliveQuery() {
	return keepAliveQuery;
  }

  Duration getKeepAliveDuration() {
	return keepAliveDuration;
  }

  public DriverManagerDataSourceBuilder build(String url) {
	if (null != driverClass && !driverClass.isEmpty()) {
	  if (driverClassLoaded.compareAndSet(false, true)) {
		try {
		  Class.forName(driverClass);
		} catch (ClassNotFoundException e) {
		  // ignore
		}
	  }
	}
	return withURL(url);
  }

  Connection getConnection() throws SQLException {
	if (user == null || password == null) return DriverManager.getConnection(url);
	if (properties.isEmpty()) return DriverManager.getConnection(url, user, password);
	properties.setProperty(PARAM_USER, user);
	properties.setProperty(PARAM_PASSWORD, password);
	return DriverManager.getConnection(url, properties);
  }

}