package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;

import java.io.Closeable;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

public final class DataSourceMethodDispatcher {

  private final DataSource dataSource;
  private final ExternalCallRecorder externalCallRecorder;

  public DataSourceMethodDispatcher(@NonNull DataSource dataSource,
          @NonNull ExternalCallRecorder externalCallRecorder) {
    this.dataSource = dataSource;
    this.externalCallRecorder = externalCallRecorder;
  }

  public Object dispatch(@NonNull Method method, @Nullable Object[] args) throws Throwable {
    return switch (method.getName()) {
      case "getConnection" -> handleGetConnection(args);
      case "getLogWriter" -> dataSource.getLogWriter();
      case "setLogWriter" -> {
        dataSource.setLogWriter((PrintWriter) require(args, 0));
        yield null;
      }
      case "getLoginTimeout" -> dataSource.getLoginTimeout();
      case "setLoginTimeout" -> {
        dataSource.setLoginTimeout((int) require(args, 0));
        yield null;
      }
      case "getParentLogger" -> dataSource.getParentLogger();
      case "createConnectionBuilder" -> dataSource.createConnectionBuilder();
      case "createShardingKeyBuilder" -> dataSource.createShardingKeyBuilder();
      case "unwrap" -> dataSource.unwrap((Class<?>) require(args, 0));
      case "isWrapperFor" -> dataSource.isWrapperFor((Class<?>) require(args, 0));
      case "close" -> {
        ((Closeable) dataSource).close();
        yield null;
      }
      default -> throw new UnsupportedOperationException(
              "Unhandled DataSource method: " + method);
    };
  }

  private Object handleGetConnection(@Nullable Object[] args) throws SQLException {
    Connection connection;
    if (args == null || args.length == 0) {
      connection = dataSource.getConnection();
    } else {
      connection = dataSource.getConnection((String) args[0], (String) args[1]);
    }
    if (connection == null) {
      return null;
    }
    return wrapConnection(connection);
  }

  private Object wrapConnection(@NonNull Connection connection) {
    ConnectionInvocationHandler handler = new ConnectionInvocationHandler(
            connection, externalCallRecorder);
    ClassLoader classLoader = connection.getClass().getClassLoader();
    if (classLoader == null) {
      classLoader = ConnectionInvocationHandler.class.getClassLoader();
    }
    return Proxy.newProxyInstance(classLoader, new Class<?>[]{Connection.class}, handler);
  }

  private static Object require(@Nullable Object[] args, int index) {
    if (args == null || args.length <= index) {
      throw new IllegalArgumentException("Missing argument at index " + index);
    }
    return args[index];
  }
}
