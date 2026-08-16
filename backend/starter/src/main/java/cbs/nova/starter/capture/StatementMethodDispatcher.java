package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

public final class StatementMethodDispatcher {

  private static final String OPERATION_BATCH = "BATCH";

  private final Statement statement;
  private final @Nullable PreparedStatement preparedStatement;
  private final @Nullable CallableStatement callableStatement;
  private final @Nullable String sql;
  private final String target;
  private final ExternalCallRecorder externalCallRecorder;

  public StatementMethodDispatcher(@NonNull Object delegate, @Nullable String sql,
          @NonNull String target, @NonNull ExternalCallRecorder externalCallRecorder) {
    this.statement = (Statement) delegate;
    this.preparedStatement = delegate instanceof PreparedStatement
            ? (PreparedStatement) delegate
            : null;
    this.callableStatement = delegate instanceof CallableStatement
            ? (CallableStatement) delegate
            : null;
    this.sql = sql;
    this.target = target;
    this.externalCallRecorder = externalCallRecorder;
  }

  public Object dispatch(@NonNull Method method, @Nullable Object[] args) throws Throwable {
    return switch (method.getName()) {
      case "executeQuery" -> handleExecuteQuery(args);
      case "executeUpdate" -> handleExecuteUpdate(args);
      case "execute" -> handleExecute(args);
      case "executeBatch" -> handleExecuteBatch(args);
      case "executeLargeUpdate" -> handleExecuteLargeUpdate(args);
      case "executeLargeBatch" -> handleExecuteLargeBatch(args);
      case "addBatch" -> {
        if (args == null || args.length == 0) {
          preparedStatement.addBatch();
          yield null;
        } else if (args.length == 1 && args[0] instanceof String) {
          statement.addBatch((String) args[0]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for addBatch: " + (args == null ? 0 : args.length) + " args");
      }
      case "cancel" -> {
        statement.cancel();
        yield null;
      }
      case "clearBatch" -> {
        statement.clearBatch();
        yield null;
      }
      case "clearParameters" -> {
        preparedStatement.clearParameters();
        yield null;
      }
      case "clearWarnings" -> {
        statement.clearWarnings();
        yield null;
      }
      case "close" -> {
        statement.close();
        yield null;
      }
      case "closeOnCompletion" -> {
        statement.closeOnCompletion();
        yield null;
      }
      case "enquoteIdentifier" -> statement.enquoteIdentifier((String) args[0], (boolean) args[1]);
      case "enquoteLiteral" -> statement.enquoteLiteral((String) args[0]);
      case "enquoteNCharLiteral" -> statement.enquoteNCharLiteral((String) args[0]);
      case "getArray" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getArray((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getArray((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getArray: " + (args == null ? 0 : args.length) + " args");
      }
      case "getBigDecimal" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          yield callableStatement.getBigDecimal((int) args[0], (int) args[1]);
        } else if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getBigDecimal((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getBigDecimal((String) args[0]);
        }
        throw new UnsupportedOperationException("Unexpected overload for getBigDecimal: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "getBlob" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getBlob((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getBlob((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getBlob: " + (args == null ? 0 : args.length) + " args");
      }
      case "getBoolean" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getBoolean((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getBoolean((String) args[0]);
        }
        throw new UnsupportedOperationException("Unexpected overload for getBoolean: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "getByte" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getByte((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getByte((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getByte: " + (args == null ? 0 : args.length) + " args");
      }
      case "getBytes" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getBytes((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getBytes((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getBytes: " + (args == null ? 0 : args.length) + " args");
      }
      case "getCharacterStream" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getCharacterStream((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getCharacterStream((String) args[0]);
        }
        throw new UnsupportedOperationException("Unexpected overload for getCharacterStream: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "getClob" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getClob((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getClob((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getClob: " + (args == null ? 0 : args.length) + " args");
      }
      case "getConnection" -> statement.getConnection();
      case "getDate" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getDate((int) args[0]);
        } else if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Calendar) {
          yield callableStatement.getDate((int) args[0], (Calendar) args[1]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getDate((String) args[0]);
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Calendar) {
          yield callableStatement.getDate((String) args[0], (Calendar) args[1]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getDate: " + (args == null ? 0 : args.length) + " args");
      }
      case "getDouble" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getDouble((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getDouble((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getDouble: " + (args == null ? 0 : args.length) + " args");
      }
      case "getFetchDirection" -> statement.getFetchDirection();
      case "getFetchSize" -> statement.getFetchSize();
      case "getFloat" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getFloat((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getFloat((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getFloat: " + (args == null ? 0 : args.length) + " args");
      }
      case "getGeneratedKeys" -> statement.getGeneratedKeys();
      case "getInt" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getInt((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getInt((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getInt: " + (args == null ? 0 : args.length) + " args");
      }
      case "getLargeMaxRows" -> statement.getLargeMaxRows();
      case "getLargeUpdateCount" -> statement.getLargeUpdateCount();
      case "getLong" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getLong((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getLong((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getLong: " + (args == null ? 0 : args.length) + " args");
      }
      case "getMaxFieldSize" -> statement.getMaxFieldSize();
      case "getMaxRows" -> statement.getMaxRows();
      case "getMetaData" -> preparedStatement.getMetaData();
      case "getMoreResults" -> {
        if (args == null || args.length == 0) {
          yield statement.getMoreResults();
        } else if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield statement.getMoreResults((int) args[0]);
        }
        throw new UnsupportedOperationException("Unexpected overload for getMoreResults: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "getNCharacterStream" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getNCharacterStream((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getNCharacterStream((String) args[0]);
        }
        throw new UnsupportedOperationException("Unexpected overload for getNCharacterStream: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "getNClob" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getNClob((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getNClob((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getNClob: " + (args == null ? 0 : args.length) + " args");
      }
      case "getNString" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getNString((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getNString((String) args[0]);
        }
        throw new UnsupportedOperationException("Unexpected overload for getNString: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "getObject" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getObject((int) args[0]);
        } else if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Map) {
          yield callableStatement.getObject((int) args[0], (Map) args[1]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getObject((String) args[0]);
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Map) {
          yield callableStatement.getObject((String) args[0], (Map) args[1]);
        } else if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Class) {
          yield callableStatement.getObject((int) args[0], (Class) args[1]);
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Class) {
          yield callableStatement.getObject((String) args[0], (Class) args[1]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getObject: " + (args == null ? 0 : args.length) + " args");
      }
      case "getParameterMetaData" -> preparedStatement.getParameterMetaData();
      case "getQueryTimeout" -> statement.getQueryTimeout();
      case "getRef" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getRef((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getRef((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getRef: " + (args == null ? 0 : args.length) + " args");
      }
      case "getResultSet" -> statement.getResultSet();
      case "getResultSetConcurrency" -> statement.getResultSetConcurrency();
      case "getResultSetHoldability" -> statement.getResultSetHoldability();
      case "getResultSetType" -> statement.getResultSetType();
      case "getRowId" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getRowId((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getRowId((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getRowId: " + (args == null ? 0 : args.length) + " args");
      }
      case "getSQLXML" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getSQLXML((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getSQLXML((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getSQLXML: " + (args == null ? 0 : args.length) + " args");
      }
      case "getShort" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getShort((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getShort((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getShort: " + (args == null ? 0 : args.length) + " args");
      }
      case "getString" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getString((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getString((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getString: " + (args == null ? 0 : args.length) + " args");
      }
      case "getTime" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getTime((int) args[0]);
        } else if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Calendar) {
          yield callableStatement.getTime((int) args[0], (Calendar) args[1]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getTime((String) args[0]);
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Calendar) {
          yield callableStatement.getTime((String) args[0], (Calendar) args[1]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getTime: " + (args == null ? 0 : args.length) + " args");
      }
      case "getTimestamp" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getTimestamp((int) args[0]);
        } else if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Calendar) {
          yield callableStatement.getTimestamp((int) args[0], (Calendar) args[1]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getTimestamp((String) args[0]);
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Calendar) {
          yield callableStatement.getTimestamp((String) args[0], (Calendar) args[1]);
        }
        throw new UnsupportedOperationException("Unexpected overload for getTimestamp: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "getURL" -> {
        if (args.length == 1 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          yield callableStatement.getURL((int) args[0]);
        } else if (args.length == 1 && args[0] instanceof String) {
          yield callableStatement.getURL((String) args[0]);
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for getURL: " + (args == null ? 0 : args.length) + " args");
      }
      case "getUpdateCount" -> statement.getUpdateCount();
      case "getWarnings" -> statement.getWarnings();
      case "isCloseOnCompletion" -> statement.isCloseOnCompletion();
      case "isClosed" -> statement.isClosed();
      case "isWrapperFor" -> statement.isWrapperFor((Class<?>) args[0]);
      case "unwrap" -> statement.unwrap((Class<?>) args[0]);
      case "isPoolable" -> statement.isPoolable();
      case "isSimpleIdentifier" -> statement.isSimpleIdentifier((String) args[0]);
      case "registerOutParameter" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          callableStatement.registerOutParameter((int) args[0], (int) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.registerOutParameter((int) args[0], (int) args[1], (int) args[2]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)
                && args[2] instanceof String) {
          callableStatement.registerOutParameter((int) args[0], (int) args[1], (String) args[2]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          callableStatement.registerOutParameter((String) args[0], (int) args[1]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String
                && (args[1] instanceof Number || args[1] instanceof Boolean)
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.registerOutParameter((String) args[0], (int) args[1], (int) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String
                && (args[1] instanceof Number || args[1] instanceof Boolean)
                && args[2] instanceof String) {
          callableStatement.registerOutParameter((String) args[0], (int) args[1], (String) args[2]);
          yield null;
        } else if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof SQLType) {
          callableStatement.registerOutParameter((int) args[0], (SQLType) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof SQLType
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.registerOutParameter((int) args[0], (SQLType) args[1], (int) args[2]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof SQLType && args[2] instanceof String) {
          callableStatement.registerOutParameter((int) args[0], (SQLType) args[1],
                  (String) args[2]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof SQLType) {
          callableStatement.registerOutParameter((String) args[0], (SQLType) args[1]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof SQLType
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.registerOutParameter((String) args[0], (SQLType) args[1],
                  (int) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof SQLType
                && args[2] instanceof String) {
          callableStatement.registerOutParameter((String) args[0], (SQLType) args[1],
                  (String) args[2]);
          yield null;
        }
        throw new UnsupportedOperationException("Unexpected overload for registerOutParameter: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "setArray" -> {
        preparedStatement.setArray((int) args[0], (Array) args[1]);
        yield null;
      }
      case "setAsciiStream" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof InputStream) {
          preparedStatement.setAsciiStream((int) args[0], (InputStream) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String
                && args[1] instanceof InputStream) {
          callableStatement.setAsciiStream((String) args[0], (InputStream) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof InputStream
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          preparedStatement.setAsciiStream((int) args[0], (InputStream) args[1], (int) args[2]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof InputStream
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          preparedStatement.setAsciiStream((int) args[0], (InputStream) args[1], (long) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof InputStream
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.setAsciiStream((String) args[0], (InputStream) args[1], (int) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof InputStream
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.setAsciiStream((String) args[0], (InputStream) args[1], (long) args[2]);
          yield null;
        }
        throw new UnsupportedOperationException("Unexpected overload for setAsciiStream: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "setBigDecimal" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof BigDecimal) {
          preparedStatement.setBigDecimal((int) args[0], (BigDecimal) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof BigDecimal) {
          callableStatement.setBigDecimal((String) args[0], (BigDecimal) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException("Unexpected overload for setBigDecimal: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "setBinaryStream" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof InputStream) {
          preparedStatement.setBinaryStream((int) args[0], (InputStream) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String
                && args[1] instanceof InputStream) {
          callableStatement.setBinaryStream((String) args[0], (InputStream) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof InputStream
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          preparedStatement.setBinaryStream((int) args[0], (InputStream) args[1], (int) args[2]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof InputStream
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          preparedStatement.setBinaryStream((int) args[0], (InputStream) args[1], (long) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof InputStream
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.setBinaryStream((String) args[0], (InputStream) args[1], (int) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof InputStream
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.setBinaryStream((String) args[0], (InputStream) args[1],
                  (long) args[2]);
          yield null;
        }
        throw new UnsupportedOperationException("Unexpected overload for setBinaryStream: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "setBlob" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Blob) {
          preparedStatement.setBlob((int) args[0], (Blob) args[1]);
          yield null;
        } else if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof InputStream) {
          preparedStatement.setBlob((int) args[0], (InputStream) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Blob) {
          callableStatement.setBlob((String) args[0], (Blob) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String
                && args[1] instanceof InputStream) {
          callableStatement.setBlob((String) args[0], (InputStream) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof InputStream
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          preparedStatement.setBlob((int) args[0], (InputStream) args[1], (long) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof InputStream
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.setBlob((String) args[0], (InputStream) args[1], (long) args[2]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setBlob: " + (args == null ? 0 : args.length) + " args");
      }
      case "setBoolean" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          preparedStatement.setBoolean((int) args[0], (boolean) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          callableStatement.setBoolean((String) args[0], (boolean) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException("Unexpected overload for setBoolean: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "setByte" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          preparedStatement.setByte((int) args[0], (byte) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          callableStatement.setByte((String) args[0], (byte) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setByte: " + (args == null ? 0 : args.length) + " args");
      }
      case "setBytes" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof byte[]) {
          preparedStatement.setBytes((int) args[0], (byte[]) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof byte[]) {
          callableStatement.setBytes((String) args[0], (byte[]) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setBytes: " + (args == null ? 0 : args.length) + " args");
      }
      case "setCharacterStream" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Reader) {
          preparedStatement.setCharacterStream((int) args[0], (Reader) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Reader) {
          callableStatement.setCharacterStream((String) args[0], (Reader) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Reader
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          preparedStatement.setCharacterStream((int) args[0], (Reader) args[1], (int) args[2]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Reader
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          preparedStatement.setCharacterStream((int) args[0], (Reader) args[1], (long) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof Reader
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.setCharacterStream((String) args[0], (Reader) args[1], (int) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof Reader
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.setCharacterStream((String) args[0], (Reader) args[1], (long) args[2]);
          yield null;
        }
        throw new UnsupportedOperationException("Unexpected overload for setCharacterStream: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "setClob" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Clob) {
          preparedStatement.setClob((int) args[0], (Clob) args[1]);
          yield null;
        } else if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Reader) {
          preparedStatement.setClob((int) args[0], (Reader) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Clob) {
          callableStatement.setClob((String) args[0], (Clob) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Reader) {
          callableStatement.setClob((String) args[0], (Reader) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Reader
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          preparedStatement.setClob((int) args[0], (Reader) args[1], (long) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof Reader
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.setClob((String) args[0], (Reader) args[1], (long) args[2]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setClob: " + (args == null ? 0 : args.length) + " args");
      }
      case "setCursorName" -> {
        statement.setCursorName((String) args[0]);
        yield null;
      }
      case "setDate" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Date) {
          preparedStatement.setDate((int) args[0], (Date) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Date) {
          callableStatement.setDate((String) args[0], (Date) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Date && args[2] instanceof Calendar) {
          preparedStatement.setDate((int) args[0], (Date) args[1], (Calendar) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof Date
                && args[2] instanceof Calendar) {
          callableStatement.setDate((String) args[0], (Date) args[1], (Calendar) args[2]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setDate: " + (args == null ? 0 : args.length) + " args");
      }
      case "setDouble" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          preparedStatement.setDouble((int) args[0], (double) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          callableStatement.setDouble((String) args[0], (double) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setDouble: " + (args == null ? 0 : args.length) + " args");
      }
      case "setEscapeProcessing" -> {
        statement.setEscapeProcessing((boolean) args[0]);
        yield null;
      }
      case "setFetchDirection" -> {
        statement.setFetchDirection((int) args[0]);
        yield null;
      }
      case "setFetchSize" -> {
        statement.setFetchSize((int) args[0]);
        yield null;
      }
      case "setFloat" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          preparedStatement.setFloat((int) args[0], (float) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          callableStatement.setFloat((String) args[0], (float) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setFloat: " + (args == null ? 0 : args.length) + " args");
      }
      case "setInt" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          preparedStatement.setInt((int) args[0], (int) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          callableStatement.setInt((String) args[0], (int) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setInt: " + (args == null ? 0 : args.length) + " args");
      }
      case "setLargeMaxRows" -> {
        statement.setLargeMaxRows((long) args[0]);
        yield null;
      }
      case "setLong" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          preparedStatement.setLong((int) args[0], (long) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          callableStatement.setLong((String) args[0], (long) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setLong: " + (args == null ? 0 : args.length) + " args");
      }
      case "setMaxFieldSize" -> {
        statement.setMaxFieldSize((int) args[0]);
        yield null;
      }
      case "setMaxRows" -> {
        statement.setMaxRows((int) args[0]);
        yield null;
      }
      case "setNCharacterStream" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Reader) {
          preparedStatement.setNCharacterStream((int) args[0], (Reader) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Reader) {
          callableStatement.setNCharacterStream((String) args[0], (Reader) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Reader
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          preparedStatement.setNCharacterStream((int) args[0], (Reader) args[1], (long) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof Reader
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.setNCharacterStream((String) args[0], (Reader) args[1], (long) args[2]);
          yield null;
        }
        throw new UnsupportedOperationException("Unexpected overload for setNCharacterStream: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "setNClob" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof NClob) {
          preparedStatement.setNClob((int) args[0], (NClob) args[1]);
          yield null;
        } else if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Reader) {
          preparedStatement.setNClob((int) args[0], (Reader) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof NClob) {
          callableStatement.setNClob((String) args[0], (NClob) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Reader) {
          callableStatement.setNClob((String) args[0], (Reader) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Reader
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          preparedStatement.setNClob((int) args[0], (Reader) args[1], (long) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof Reader
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.setNClob((String) args[0], (Reader) args[1], (long) args[2]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setNClob: " + (args == null ? 0 : args.length) + " args");
      }
      case "setNString" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof String) {
          preparedStatement.setNString((int) args[0], (String) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof String) {
          callableStatement.setNString((String) args[0], (String) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException("Unexpected overload for setNString: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "setNull" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          preparedStatement.setNull((int) args[0], (int) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          callableStatement.setNull((String) args[0], (int) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)
                && args[2] instanceof String) {
          preparedStatement.setNull((int) args[0], (int) args[1], (String) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String
                && (args[1] instanceof Number || args[1] instanceof Boolean)
                && args[2] instanceof String) {
          callableStatement.setNull((String) args[0], (int) args[1], (String) args[2]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setNull: " + (args == null ? 0 : args.length) + " args");
      }
      case "setObject" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)) {
          preparedStatement.setObject((int) args[0], args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String) {
          callableStatement.setObject((String) args[0], args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          preparedStatement.setObject((int) args[0], args[1], (int) args[2]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[2] instanceof SQLType) {
          preparedStatement.setObject((int) args[0], args[1], (SQLType) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String
                && (args[2] instanceof Number || args[2] instanceof Boolean)) {
          callableStatement.setObject((String) args[0], args[1], (int) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[2] instanceof SQLType) {
          callableStatement.setObject((String) args[0], args[1], (SQLType) args[2]);
          yield null;
        } else if (args.length == 4 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[2] instanceof Number || args[2] instanceof Boolean)
                && (args[3] instanceof Number || args[3] instanceof Boolean)) {
          preparedStatement.setObject((int) args[0], args[1], (int) args[2],
                  (int) args[3]);
          yield null;
        } else if (args.length == 4 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[2] instanceof SQLType
                && (args[3] instanceof Number || args[3] instanceof Boolean)) {
          preparedStatement.setObject((int) args[0], args[1], (SQLType) args[2],
                  (int) args[3]);
          yield null;
        } else if (args.length == 4 && args[0] instanceof String
                && (args[2] instanceof Number || args[2] instanceof Boolean)
                && (args[3] instanceof Number || args[3] instanceof Boolean)) {
          callableStatement.setObject((String) args[0], args[1], (int) args[2],
                  (int) args[3]);
          yield null;
        } else if (args.length == 4 && args[0] instanceof String && args[2] instanceof SQLType
                && (args[3] instanceof Number || args[3] instanceof Boolean)) {
          callableStatement.setObject((String) args[0], args[1], (SQLType) args[2],
                  (int) args[3]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setObject: " + (args == null ? 0 : args.length) + " args");
      }
      case "setPoolable" -> {
        statement.setPoolable((boolean) args[0]);
        yield null;
      }
      case "setQueryTimeout" -> {
        statement.setQueryTimeout((int) args[0]);
        yield null;
      }
      case "setRef" -> {
        preparedStatement.setRef((int) args[0], (Ref) args[1]);
        yield null;
      }
      case "setRowId" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof RowId) {
          preparedStatement.setRowId((int) args[0], (RowId) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof RowId) {
          callableStatement.setRowId((String) args[0], (RowId) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setRowId: " + (args == null ? 0 : args.length) + " args");
      }
      case "setSQLXML" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof SQLXML) {
          preparedStatement.setSQLXML((int) args[0], (SQLXML) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof SQLXML) {
          callableStatement.setSQLXML((String) args[0], (SQLXML) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setSQLXML: " + (args == null ? 0 : args.length) + " args");
      }
      case "setShort" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          preparedStatement.setShort((int) args[0], (short) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String
                && (args[1] instanceof Number || args[1] instanceof Boolean)) {
          callableStatement.setShort((String) args[0], (short) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setShort: " + (args == null ? 0 : args.length) + " args");
      }
      case "setString" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof String) {
          preparedStatement.setString((int) args[0], (String) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof String) {
          callableStatement.setString((String) args[0], (String) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setString: " + (args == null ? 0 : args.length) + " args");
      }
      case "setTime" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Time) {
          preparedStatement.setTime((int) args[0], (Time) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Time) {
          callableStatement.setTime((String) args[0], (Time) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Time && args[2] instanceof Calendar) {
          preparedStatement.setTime((int) args[0], (Time) args[1], (Calendar) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof Time
                && args[2] instanceof Calendar) {
          callableStatement.setTime((String) args[0], (Time) args[1], (Calendar) args[2]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setTime: " + (args == null ? 0 : args.length) + " args");
      }
      case "setTimestamp" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Timestamp) {
          preparedStatement.setTimestamp((int) args[0], (Timestamp) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof Timestamp) {
          callableStatement.setTimestamp((String) args[0], (Timestamp) args[1]);
          yield null;
        } else if (args.length == 3 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof Timestamp && args[2] instanceof Calendar) {
          preparedStatement.setTimestamp((int) args[0], (Timestamp) args[1], (Calendar) args[2]);
          yield null;
        } else if (args.length == 3 && args[0] instanceof String && args[1] instanceof Timestamp
                && args[2] instanceof Calendar) {
          callableStatement.setTimestamp((String) args[0], (Timestamp) args[1], (Calendar) args[2]);
          yield null;
        }
        throw new UnsupportedOperationException("Unexpected overload for setTimestamp: "
                + (args == null ? 0 : args.length) + " args");
      }
      case "setURL" -> {
        if (args.length == 2 && (args[0] instanceof Number || args[0] instanceof Boolean)
                && args[1] instanceof URL) {
          preparedStatement.setURL((int) args[0], (URL) args[1]);
          yield null;
        } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof URL) {
          callableStatement.setURL((String) args[0], (URL) args[1]);
          yield null;
        }
        throw new UnsupportedOperationException(
                "Unexpected overload for setURL: " + (args == null ? 0 : args.length) + " args");
      }
      case "setUnicodeStream" -> {
        preparedStatement.setUnicodeStream((int) args[0], (InputStream) args[1], (int) args[2]);
        yield null;
      }
      case "wasNull" -> callableStatement.wasNull();
      default -> throw new UnsupportedOperationException("Unhandled Statement method: " + method);
    };
  }

  private Object handleExecuteQuery(@Nullable Object[] args) throws SQLException {
    if (args == null || args.length == 0) {
      recordCall(sql);
      return preparedStatement.executeQuery();
    }
    String argumentSql = (String) args[0];
    recordCall(sql != null ? sql : argumentSql);
    return statement.executeQuery(argumentSql);
  }

  private Object handleExecuteUpdate(@Nullable Object[] args) throws SQLException {
    if (args == null || args.length == 0) {
      recordCall(sql);
      return preparedStatement.executeUpdate();
    }
    String argumentSql = (String) args[0];
    recordCall(sql != null ? sql : argumentSql);
    if (args.length == 1) {
      return statement.executeUpdate(argumentSql);
    } else if (args.length == 2 && args[1] instanceof Number n) {
      return statement.executeUpdate(argumentSql, n.intValue());
    } else if (args.length == 2 && args[1] instanceof int[] keys) {
      return statement.executeUpdate(argumentSql, keys);
    } else if (args.length == 2 && args[1] instanceof String[] columns) {
      return statement.executeUpdate(argumentSql, columns);
    }
    throw new UnsupportedOperationException("Unexpected executeUpdate overload");
  }

  private Object handleExecute(@Nullable Object[] args) throws SQLException {
    if (args == null || args.length == 0) {
      recordCall(sql);
      return preparedStatement.execute();
    }
    String argumentSql = (String) args[0];
    recordCall(sql != null ? sql : argumentSql);
    if (args.length == 1) {
      return statement.execute(argumentSql);
    } else if (args.length == 2 && args[1] instanceof Number n) {
      return statement.execute(argumentSql, n.intValue());
    } else if (args.length == 2 && args[1] instanceof int[] keys) {
      return statement.execute(argumentSql, keys);
    } else if (args.length == 2 && args[1] instanceof String[] columns) {
      return statement.execute(argumentSql, columns);
    }
    throw new UnsupportedOperationException("Unexpected execute overload");
  }

  private Object handleExecuteBatch(@Nullable Object[] args) throws SQLException {
    recordBatch();
    return statement.executeBatch();
  }

  private Object handleExecuteLargeUpdate(@Nullable Object[] args) throws SQLException {
    if (args == null || args.length == 0) {
      recordCall(sql);
      return preparedStatement.executeLargeUpdate();
    }
    String argumentSql = (String) args[0];
    recordCall(sql != null ? sql : argumentSql);
    if (args.length == 1) {
      return statement.executeLargeUpdate(argumentSql);
    } else if (args.length == 2 && args[1] instanceof Number n) {
      return statement.executeLargeUpdate(argumentSql, n.intValue());
    } else if (args.length == 2 && args[1] instanceof int[] keys) {
      return statement.executeLargeUpdate(argumentSql, keys);
    } else if (args.length == 2 && args[1] instanceof String[] columns) {
      return statement.executeLargeUpdate(argumentSql, columns);
    }
    throw new UnsupportedOperationException("Unexpected executeLargeUpdate overload");
  }

  private Object handleExecuteLargeBatch(@Nullable Object[] args) throws SQLException {
    recordBatch();
    return statement.executeLargeBatch();
  }

  private void recordCall(@Nullable String effectiveSql) {
    String operation = firstSqlToken(effectiveSql);
    externalCallRecorder.record(ExternalCallRecorder.TYPE_DATABASE, target, operation,
            effectiveSql);
  }

  private void recordBatch() {
    externalCallRecorder.record(ExternalCallRecorder.TYPE_DATABASE, target, OPERATION_BATCH, sql);
  }

  private static @NonNull String firstSqlToken(@Nullable String sql) {
    if (sql == null) {
      return "UNKNOWN";
    }
    String trimmed = sql.trim();
    if (trimmed.isEmpty()) {
      return "UNKNOWN";
    }
    int idx = 0;
    while (idx < trimmed.length() && Character.isWhitespace(trimmed.charAt(idx))) {
      idx++;
    }
    int end = idx;
    while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
      end++;
    }
    return trimmed.substring(idx, end).toUpperCase();
  }
}
