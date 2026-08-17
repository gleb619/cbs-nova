package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.jspecify.annotations.NonNull;

import javax.sql.CommonDataSource;
import javax.sql.DataSource;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ConnectionBuilder;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.ShardingKeyBuilder;
import java.sql.Wrapper;
import java.util.logging.Logger;

/**
 * Typed decorator around a real {@link DataSource}. Every method delegates with a
 * compile-time-typed call; {@link #getConnection()} wraps the returned connection in a
 * {@link RecordingConnection} so statement execution can be recorded by the
 * {@link ExternalCallRecorder}.
 */
public class RecordingDataSource
        implements
          DataSource,
          CommonDataSource,
          Wrapper,
          AutoCloseable,
          Closeable {

  private final DataSource delegate;
  private final ExternalCallRecorder externalCallRecorder;

  public RecordingDataSource(@NonNull DataSource delegate,
          @NonNull ExternalCallRecorder externalCallRecorder) {
    this.delegate = delegate;
    this.externalCallRecorder = externalCallRecorder;
  }

  @Override
  public Connection getConnection() throws SQLException {
    Connection connection = delegate.getConnection();
    return connection == null ? null : wrapConnection(connection);
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    Connection connection = delegate.getConnection(username, password);
    return connection == null ? null : wrapConnection(connection);
  }

  private Connection wrapConnection(@NonNull Connection connection) {
    return new RecordingConnection(connection, externalCallRecorder);
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return delegate.getLogWriter();
  }

  @Override
  public void setLogWriter(PrintWriter out) throws SQLException {
    delegate.setLogWriter(out);
  }

  @Override
  public void setLoginTimeout(int seconds) throws SQLException {
    delegate.setLoginTimeout(seconds);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return delegate.getLoginTimeout();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return delegate.getParentLogger();
  }

  @Override
  public ConnectionBuilder createConnectionBuilder() throws SQLException {
    return delegate.createConnectionBuilder();
  }

  @Override
  public ShardingKeyBuilder createShardingKeyBuilder() throws SQLException {
    return delegate.createShardingKeyBuilder();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return delegate.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return delegate.isWrapperFor(iface);
  }

  @Override
  public void close() throws IOException {
    ((Closeable) delegate).close();
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return "RecordingDataSource[" + delegate + "]";
  }
}
