package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public final class ConnectionMethodDispatcher {

  private final Connection connection;
  private final ExternalCallRecorder externalCallRecorder;

  public ConnectionMethodDispatcher(@NonNull Connection connection,
          @NonNull ExternalCallRecorder externalCallRecorder) {
    this.connection = connection;
    this.externalCallRecorder = externalCallRecorder;
  }

  public Object dispatch(@NonNull Method method, @Nullable Object[] args) throws Throwable {
    return switch (method.getName()) {
      case "prepareStatement" -> handlePrepareStatement(args);
      case "prepareCall" -> handlePrepareCall(args);
      case "createStatement" -> handleCreateStatement(args);
      case "abort" -> {
        connection.abort((Executor) require(args, 0));
        yield null;
      }
      case "beginRequest" -> {
        connection.beginRequest();
        yield null;
      }
      case "clearWarnings" -> {
        connection.clearWarnings();
        yield null;
      }
      case "close" -> {
        connection.close();
        yield null;
      }
      case "commit" -> {
        connection.commit();
        yield null;
      }
      case "createArrayOf" ->
        connection.createArrayOf((String) require(args, 0), (Object[]) require(args, 1));
      case "createBlob" -> connection.createBlob();
      case "createClob" -> connection.createClob();
      case "createNClob" -> connection.createNClob();
      case "createSQLXML" -> connection.createSQLXML();
      case "createStruct" ->
        connection.createStruct((String) require(args, 0), (Object[]) require(args, 1));
      case "endRequest" -> {
        connection.endRequest();
        yield null;
      }
      case "getAutoCommit" -> connection.getAutoCommit();
      case "getCatalog" -> connection.getCatalog();
      case "getClientInfo" -> handleGetClientInfo(args);
      case "getHoldability" -> connection.getHoldability();
      case "getMetaData" -> connection.getMetaData();
      case "getNetworkTimeout" -> connection.getNetworkTimeout();
      case "getSchema" -> connection.getSchema();
      case "getTransactionIsolation" -> connection.getTransactionIsolation();
      case "getTypeMap" -> connection.getTypeMap();
      case "getWarnings" -> connection.getWarnings();
      case "isClosed" -> connection.isClosed();
      case "isWrapperFor" -> connection.isWrapperFor((Class<?>) require(args, 0));
      case "unwrap" -> connection.unwrap((Class<?>) require(args, 0));
      case "isReadOnly" -> connection.isReadOnly();
      case "isValid" -> connection.isValid((int) require(args, 0));
      case "nativeSQL" -> connection.nativeSQL((String) require(args, 0));
      case "releaseSavepoint" -> {
        connection.releaseSavepoint((Savepoint) require(args, 0));
        yield null;
      }
      case "rollback" -> handleRollback(args);
      case "setAutoCommit" -> {
        connection.setAutoCommit((boolean) require(args, 0));
        yield null;
      }
      case "setCatalog" -> {
        connection.setCatalog((String) require(args, 0));
        yield null;
      }
      case "setClientInfo" -> handleSetClientInfo(args);
      case "setHoldability" -> {
        connection.setHoldability((int) require(args, 0));
        yield null;
      }
      case "setNetworkTimeout" -> {
        connection.setNetworkTimeout((Executor) require(args, 0),
                (int) require(args, 1));
        yield null;
      }
      case "setReadOnly" -> {
        connection.setReadOnly((boolean) require(args, 0));
        yield null;
      }
      case "setSavepoint" -> handleSetSavepoint(args);
      case "setSchema" -> {
        connection.setSchema((String) require(args, 0));
        yield null;
      }
      case "setShardingKey" -> handleSetShardingKey(args);
      case "setShardingKeyIfValid" -> handleSetShardingKeyIfValid(args);
      case "setTransactionIsolation" -> {
        connection.setTransactionIsolation((int) require(args, 0));
        yield null;
      }
      case "setTypeMap" -> {
        connection.setTypeMap((Map<String, Class<?>>) require(args, 0));
        yield null;
      }
      default -> throw new UnsupportedOperationException("Unhandled Connection method: " + method);
    };
  }

  private Object handlePrepareStatement(@Nullable Object[] args) throws SQLException {
    PreparedStatement statement;
    String sql;
    if (args == null || args.length == 0) {
      throw new UnsupportedOperationException("prepareStatement requires arguments");
    }
    sql = (String) args[0];
    if (args.length == 1) {
      statement = connection.prepareStatement(sql);
    } else if (args.length == 2 && args[1] instanceof Number) {
      statement = connection.prepareStatement(sql, ((Number) args[1]).intValue());
    } else if (args.length == 2 && args[1] instanceof int[]) {
      statement = connection.prepareStatement(sql, (int[]) args[1]);
    } else if (args.length == 2 && args[1] instanceof String[]) {
      statement = connection.prepareStatement(sql, (String[]) args[1]);
    } else if (args.length == 3) {
      statement = connection.prepareStatement(sql, ((Number) args[1]).intValue(),
              ((Number) args[2]).intValue());
    } else if (args.length == 4) {
      statement = connection.prepareStatement(sql, ((Number) args[1]).intValue(),
              ((Number) args[2]).intValue(), ((Number) args[3]).intValue());
    } else {
      throw new UnsupportedOperationException("Unexpected prepareStatement overload");
    }
    return statement == null ? null : wrapStatement(statement, sql);
  }

  private Object handlePrepareCall(@Nullable Object[] args) throws SQLException {
    CallableStatement statement;
    String sql;
    if (args == null || args.length == 0) {
      throw new UnsupportedOperationException("prepareCall requires arguments");
    }
    sql = (String) args[0];
    if (args.length == 1) {
      statement = connection.prepareCall(sql);
    } else if (args.length == 3) {
      statement = connection.prepareCall(sql, ((Number) args[1]).intValue(),
              ((Number) args[2]).intValue());
    } else if (args.length == 4) {
      statement = connection.prepareCall(sql, ((Number) args[1]).intValue(),
              ((Number) args[2]).intValue(), ((Number) args[3]).intValue());
    } else {
      throw new UnsupportedOperationException("Unexpected prepareCall overload");
    }
    return statement == null ? null : wrapStatement(statement, sql);
  }

  private Object handleCreateStatement(@Nullable Object[] args) throws SQLException {
    Statement statement;
    if (args == null || args.length == 0) {
      statement = connection.createStatement();
    } else if (args.length == 2) {
      statement = connection.createStatement(((Number) args[0]).intValue(),
              ((Number) args[1]).intValue());
    } else if (args.length == 3) {
      statement = connection.createStatement(((Number) args[0]).intValue(),
              ((Number) args[1]).intValue(), ((Number) args[2]).intValue());
    } else {
      throw new UnsupportedOperationException("Unexpected createStatement overload");
    }
    return statement == null ? null : wrapStatement(statement, null);
  }

  private Object handleGetClientInfo(@Nullable Object[] args) throws SQLException {
    if (args == null || args.length == 0) {
      return connection.getClientInfo();
    }
    return connection.getClientInfo((String) args[0]);
  }

  private Object handleRollback(@Nullable Object[] args) throws SQLException {
    if (args == null || args.length == 0) {
      connection.rollback();
    } else {
      connection.rollback((Savepoint) args[0]);
    }
    return null;
  }

  private Object handleSetClientInfo(@Nullable Object[] args)
          throws SQLClientInfoException {
    if (args == null || args.length == 0) {
      throw new IllegalArgumentException("setClientInfo requires arguments");
    }
    if (args[0] instanceof Properties props) {
      connection.setClientInfo(props);
    } else {
      connection.setClientInfo((String) args[0], (String) args[1]);
    }
    return null;
  }

  private Object handleSetSavepoint(@Nullable Object[] args) throws SQLException {
    if (args == null || args.length == 0) {
      return connection.setSavepoint();
    }
    return connection.setSavepoint((String) args[0]);
  }

  private Object handleSetShardingKey(@Nullable Object[] args) throws SQLException {
    if (args == null || args.length == 0) {
      throw new IllegalArgumentException("setShardingKey requires arguments");
    }
    if (args.length == 1) {
      connection.setShardingKey((ShardingKey) args[0]);
    } else {
      connection.setShardingKey((ShardingKey) args[0], (ShardingKey) args[1]);
    }
    return null;
  }

  private Object handleSetShardingKeyIfValid(@Nullable Object[] args) throws SQLException {
    if (args == null || args.length < 2) {
      throw new IllegalArgumentException("setShardingKeyIfValid requires arguments");
    }
    if (args.length == 2) {
      return connection.setShardingKeyIfValid((ShardingKey) args[0],
              ((Number) args[1]).intValue());
    }
    return connection.setShardingKeyIfValid((ShardingKey) args[0],
            (ShardingKey) args[1], ((Number) args[2]).intValue());
  }

  private Object wrapStatement(@NonNull Statement statement, @Nullable String sql) {
    String target = resolveTarget();
    ClassLoader classLoader = statement.getClass().getClassLoader();
    if (classLoader == null) {
      classLoader = ConnectionInvocationHandler.class.getClassLoader();
    }
    Class<?>[] interfaces = statementInterfaces(statement);
    PreparedStatementInvocationHandler handler = new PreparedStatementInvocationHandler(
            statement, sql, target, externalCallRecorder);
    return Proxy.newProxyInstance(classLoader, interfaces, handler);
  }

  private @NonNull String resolveTarget() {
    try {
      String url = connection.getMetaData().getURL();
      if (url != null && !url.isBlank()) {
        return url;
      }
    } catch (SQLException ignored) {
      // fall through to fallback
    }
    return ConnectionInvocationHandler.FALLBACK_TARGET;
  }

  private static Class<?>[] statementInterfaces(@NonNull Statement statement) {
    if (statement instanceof CallableStatement) {
      return new Class<?>[]{CallableStatement.class, PreparedStatement.class, Statement.class};
    }
    if (statement instanceof PreparedStatement) {
      return new Class<?>[]{PreparedStatement.class, Statement.class};
    }
    return new Class<?>[]{Statement.class};
  }

  private static Object require(@Nullable Object[] args, int index) {
    if (args == null || args.length <= index) {
      throw new IllegalArgumentException("Missing argument at index " + index);
    }
    return args[index];
  }
}
