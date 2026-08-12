package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * InvocationHandler that records every JDBC execution call made on a proxied
 * {@link java.sql.Statement}, {@link java.sql.PreparedStatement} or
 * {@link java.sql.CallableStatement} into the supplied {@link ExternalCallRecorder}, then delegates
 * the call to the real statement.
 *
 * <p>
 * The captured SQL string is the one passed to the factory method ({@code prepareStatement} /
 * {@code prepareCall}) or, for raw {@link java.sql.Statement#execute(String) Statement.execute}
 * family calls, the SQL supplied as the first argument. The first whitespace-separated token of the
 * SQL (upper-cased) is used as the recorded operation; {@code executeBatch} is always recorded as
 * {@code BATCH}.
 */
//TODO: Usage of reflection is forbidden, add typed handler here
public class PreparedStatementInvocationHandler implements InvocationHandler {

  private static final Set<String> RECORDED_METHODS = Set.of(
          "executeQuery",
          "executeUpdate",
          "execute",
          "executeBatch",
          "executeLargeUpdate",
          "executeLargeBatch");

  private static final String OPERATION_BATCH = "BATCH";

  private final Object delegate;
  private final String sql;
  private final String target;
  private final ExternalCallRecorder externalCallRecorder;

  public PreparedStatementInvocationHandler(@NonNull Object delegate, @Nullable String sql,
          @NonNull String target, @NonNull ExternalCallRecorder externalCallRecorder) {
    this.delegate = delegate;
    this.sql = sql;
    this.target = target;
    this.externalCallRecorder = externalCallRecorder;
  }

  @Override
  public Object invoke(@NonNull Object proxy, @NonNull Method method, @Nullable Object[] args)
          throws Throwable {
    if (RECORDED_METHODS.contains(method.getName())) {
      recordCall(method, args);
    }
    try {
      return method.invoke(delegate, args);
    } catch (InvocationTargetException ex) {
      throw ex.getCause();
    }
  }

  private void recordCall(@NonNull Method method, @Nullable Object[] args) {
    String operation;
    String payload;
    if ("executeBatch".equals(method.getName()) || "executeLargeBatch".equals(method.getName())) {
      operation = OPERATION_BATCH;
      payload = sql;
    } else {
      String effectiveSql = sql;
      if (effectiveSql == null && args != null) {
        for (Object arg : args) {
          if (arg instanceof String s) {
            effectiveSql = s;
            break;
          }
        }
      }
      operation = firstSqlToken(effectiveSql);
      payload = effectiveSql;
    }
    // DB response mocking needs the T168 interceptor SPI; this wrapper only observes.
    externalCallRecorder.findMock(ExternalCallRecorder.TYPE_DATABASE, target, operation);
    externalCallRecorder.record(ExternalCallRecorder.TYPE_DATABASE, target, operation, payload);
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
