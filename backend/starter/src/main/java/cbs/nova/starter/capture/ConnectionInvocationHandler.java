package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * InvocationHandler that intercepts JDBC operations on a proxied {@link DataSource} or
 * {@link Connection} and forwards every other call to the underlying delegate.
 *
 * <p>
 * For a {@link DataSource} delegate, {@code getConnection()} is intercepted to wrap the returned
 * connection in a JDK proxy. For a {@link Connection} delegate, the statement factory methods
 * ({@code prepareStatement}, {@code prepareCall}, {@code createStatement}) are intercepted to wrap
 * the returned statement in a JDK proxy so that JDBC execution calls can be recorded into the
 * {@link ExternalCallRecorder}.
 */
// TODO: Usage of reflection here is forbidden, we need a typed handler here(e.g. create what
// needed, and replace reflection)
public class ConnectionInvocationHandler implements InvocationHandler {

  public static final String FALLBACK_TARGET = "jdbc:datasource";

  private final Set<Method> GET_CONNECTION_METHODS = Set.of(
          methodOrThrow(DataSource.class, "getConnection"),
          methodOrThrow(DataSource.class, "getConnection", String.class, String.class));

  private final Set<String> CONNECTION_FACTORY_METHODS = Set.of(
          "prepareStatement", "prepareCall", "createStatement");

  private final Object delegate;
  private final ExternalCallRecorder externalCallRecorder;
  private final boolean delegateIsDataSource;

  public ConnectionInvocationHandler(@NonNull DataSource dataSource,
          @NonNull ExternalCallRecorder externalCallRecorder) {
    this(dataSource, dataSource, externalCallRecorder, true);
  }

  private ConnectionInvocationHandler(@NonNull Connection connection,
          @NonNull ExternalCallRecorder externalCallRecorder) {
    this(connection, connection, externalCallRecorder, false);
  }

  private ConnectionInvocationHandler(@NonNull Object primaryDelegate,
          @NonNull Object typeProbe,
          @NonNull ExternalCallRecorder externalCallRecorder,
          boolean delegateIsDataSource) {
    this.delegate = primaryDelegate;
    this.externalCallRecorder = externalCallRecorder;
    this.delegateIsDataSource = delegateIsDataSource;
  }

  @Override
  public Object invoke(@NonNull Object proxy, @NonNull Method method, @Nullable Object[] args)
          throws Throwable {
    if (delegateIsDataSource && GET_CONNECTION_METHODS.contains(method)) {
      return handleGetConnection(method, args);
    }
    if (!delegateIsDataSource && CONNECTION_FACTORY_METHODS.contains(method.getName())) {
      return handleStatementFactory(method, args);
    }
    try {
      return method.invoke(delegate, args);
    } catch (InvocationTargetException ex) {
      throw ex.getCause();
    }
  }

  private Object handleGetConnection(@NonNull Method method, @Nullable Object[] args)
          throws Throwable {
    Connection connection;
    try {
      connection = (Connection) method.invoke(delegate, args);
    } catch (InvocationTargetException ex) {
      throw ex.getCause();
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
    return Proxy.newProxyInstance(
            classLoader,
            new Class<?>[]{Connection.class},
            handler);
  }

  private Object handleStatementFactory(@NonNull Method method, @Nullable Object[] args)
          throws Throwable {
    Object statement;
    try {
      statement = method.invoke(delegate, args);
    } catch (InvocationTargetException ex) {
      throw ex.getCause();
    }
    if (statement == null) {
      return null;
    }
    String sql = extractSql(method, args);
    String target = resolveTarget();
    return wrapStatement(statement, sql, target);
  }

  private @Nullable String extractSql(@NonNull Method method, @Nullable Object[] args) {
    if (args == null) {
      return null;
    }
    if ("createStatement".equals(method.getName())) {
      return null;
    }
    for (Object arg : args) {
      if (arg instanceof String s) {
        return s;
      }
    }
    return null;
  }

  private String resolveTarget() {
    if (!delegateIsDataSource) {
      return resolveTargetFromConnection((Connection) delegate);
    }
    DataSource ds = (DataSource) delegate;
    try (Connection conn = ds.getConnection()) {
      if (conn == null) {
        return FALLBACK_TARGET;
      }
      return resolveTargetFromConnection(conn);
    } catch (SQLException ex) {
      return FALLBACK_TARGET;
    }
  }

  private @NonNull String resolveTargetFromConnection(@NonNull Connection connection) {
    try {
      String url = connection.getMetaData().getURL();
      if (url != null && !url.isBlank()) {
        return url;
      }
    } catch (SQLException ignored) {
      // fall through to fallback
    }
    return FALLBACK_TARGET;
  }

  private Object wrapStatement(@NonNull Object statement, @Nullable String sql,
          @NonNull String target) {
    ClassLoader classLoader = statement.getClass().getClassLoader();
    if (classLoader == null) {
      classLoader = ConnectionInvocationHandler.class.getClassLoader();
    }
    Class<?>[] interfaces = statementInterfaces(statement);
    PreparedStatementInvocationHandler handler = new PreparedStatementInvocationHandler(
            statement, sql, target, externalCallRecorder);
    return Proxy.newProxyInstance(classLoader, interfaces, handler);
  }

  private Class<?>[] statementInterfaces(@NonNull Object statement) {
    if (statement instanceof CallableStatement) {
      return new Class<?>[]{CallableStatement.class, PreparedStatement.class, Statement.class};
    }
    if (statement instanceof PreparedStatement) {
      return new Class<?>[]{PreparedStatement.class, Statement.class};
    }
    return new Class<?>[]{Statement.class};
  }

  private Method methodOrThrow(Class<?> owner, String name, Class<?>... params) {
    try {
      return owner.getMethod(name, params);
    } catch (NoSuchMethodException ex) {
      throw new IllegalStateException(
              "Required method not found on " + owner.getName() + ": " + name, ex);
    }
  }
}
