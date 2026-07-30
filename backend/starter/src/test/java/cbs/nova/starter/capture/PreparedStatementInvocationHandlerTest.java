package cbs.nova.starter.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cbs.nova.starter.ExternalCallTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Pure-unit tests for {@link PreparedStatementInvocationHandler}. Uses Mockito-mocked delegates and
 * a mocked {@link ExternalCallTracker} so the handler is exercised without any DB or Spring
 * context. Verifies SQL extraction, op classification, record-before-delegate ordering, and
 * pass-through behaviour for non-recorded methods.
 */
class PreparedStatementInvocationHandlerTest {

  private static final String TARGET = "jdbc:h2:mem:test";
  private static final String SQL_SELECT = "select id from orders where id = ?";

  private ExternalCallTracker tracker;
  private PreparedStatement delegate;

  @BeforeEach
  void setUp() {
    tracker = mock(ExternalCallTracker.class);
    delegate = mock(PreparedStatement.class);
  }

  private PreparedStatement newProxy(String sql) {
    PreparedStatementInvocationHandler handler = new PreparedStatementInvocationHandler(
            delegate, sql, TARGET, tracker);
    return (PreparedStatement) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{PreparedStatement.class, Statement.class},
            handler);
  }

  @Test
  void executeQueryForwardsAndRecordsWithFactorySql() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(delegate.executeQuery()).thenReturn(rs);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    ResultSet actual = proxy.executeQuery();

    assertThat(actual).isSameAs(rs);
    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT", SQL_SELECT);
    verify(delegate).executeQuery();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void executeUpdateForwardsAndRecordsWithFactorySql() throws SQLException {
    when(delegate.executeUpdate()).thenReturn(7);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    int updated = proxy.executeUpdate();

    assertThat(updated).isEqualTo(7);
    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT", SQL_SELECT);
    verify(delegate).executeUpdate();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void executeForwardsAndRecordsWithFactorySql() throws SQLException {
    when(delegate.execute()).thenReturn(true);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    boolean executed = proxy.execute();

    assertThat(executed).isTrue();
    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT", SQL_SELECT);
    verify(delegate).execute();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void executeBatchForwardsAndRecordsBatchOperation() throws SQLException {
    int[] counts = {1, 2};
    when(delegate.executeBatch()).thenReturn(counts);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    int[] actual = proxy.executeBatch();

    assertThat(actual).isSameAs(counts);
    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "BATCH");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "BATCH", SQL_SELECT);
    verify(delegate).executeBatch();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void executeLargeUpdateForwardsAndRecordsWithFactorySql() throws SQLException {
    when(delegate.executeLargeUpdate()).thenReturn(42L);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    long updated = proxy.executeLargeUpdate();

    assertThat(updated).isEqualTo(42L);
    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT", SQL_SELECT);
    verify(delegate).executeLargeUpdate();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void executeLargeBatchForwardsAndRecordsBatchOperation() throws SQLException {
    long[] counts = {10L, 20L};
    when(delegate.executeLargeBatch()).thenReturn(counts);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    long[] actual = proxy.executeLargeBatch();

    assertThat(actual).isSameAs(counts);
    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "BATCH");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "BATCH", SQL_SELECT);
    verify(delegate).executeLargeBatch();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void operationIsFirstUppercaseSqlToken() throws SQLException {
    when(delegate.execute()).thenReturn(false);
    String mixedCaseSql = "  \tInsert Into foo values (?)";

    PreparedStatement proxy = newProxy(mixedCaseSql);
    proxy.execute();

    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "INSERT");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "INSERT", mixedCaseSql);
    verify(delegate).execute();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void rawStatementExecuteWithNoFactorySqlUsesArgument() throws SQLException {
    String adHocSql = "delete from orders where id = 1";
    when(delegate.execute(adHocSql)).thenReturn(false);

    PreparedStatement proxy = newProxy(null);
    boolean executed = proxy.execute(adHocSql);

    assertThat(executed).isFalse();
    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "DELETE");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "DELETE", adHocSql);
    verify(delegate).execute(adHocSql);
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void factorySqlTakesPrecedenceOverArgumentSql() throws SQLException {
    String argSql = "select 1 from dual";
    when(delegate.executeUpdate(argSql)).thenReturn(3);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    int updated = proxy.executeUpdate(argSql);

    assertThat(updated).isEqualTo(3);
    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT", SQL_SELECT);
    verify(delegate).executeUpdate(argSql);
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void unknownSqlTokenIsUsedWhenSqlMissing() throws SQLException {
    when(delegate.executeQuery()).thenReturn(mock(ResultSet.class));

    PreparedStatement proxy = newProxy(null);
    proxy.executeQuery();

    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "UNKNOWN");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "UNKNOWN", null);
    verify(delegate).executeQuery();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void emptySqlFallsBackToUnknownOperation() throws SQLException {
    when(delegate.executeQuery()).thenReturn(mock(ResultSet.class));

    PreparedStatement proxy = newProxy("   ");
    proxy.executeQuery();

    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "UNKNOWN");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "UNKNOWN", "   ");
    verify(delegate).executeQuery();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void batchOperationIgnoresStringArguments() throws SQLException {
    int[] counts = {5};
    when(delegate.executeBatch()).thenReturn(counts);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    proxy.executeBatch();

    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "BATCH");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "BATCH", SQL_SELECT);
    verify(delegate).executeBatch();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void nonRecordedMethodsForwardAndDoNotTouchTracker() throws SQLException {
    PreparedStatement proxy = newProxy(SQL_SELECT);
    proxy.setString(1, "abc");
    proxy.close();

    verify(delegate).setString(1, "abc");
    verify(delegate).close();
    verifyNoInteractions(tracker);
  }

  @Test
  void getterMethodsForwardAndDoNotTouchTracker() throws SQLException {
    when(delegate.getResultSet()).thenReturn(mock(ResultSet.class));
    when(delegate.getUpdateCount()).thenReturn(11);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    ResultSet rs = proxy.getResultSet();
    int count = proxy.getUpdateCount();

    assertThat(rs).isNotNull();
    assertThat(count).isEqualTo(11);
    verify(delegate).getResultSet();
    verify(delegate).getUpdateCount();
    verifyNoInteractions(tracker);
  }

  @Test
  void recordHappensBeforeDelegateInvocation() throws SQLException {
    when(delegate.executeUpdate()).thenReturn(1);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    proxy.executeUpdate();

    InOrder ordered = inOrder(tracker, delegate);
    ordered.verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT");
    ordered.verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT",
            SQL_SELECT);
    ordered.verify(delegate).executeUpdate();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void delegateFailureStillRecordsAndSurfacesOriginalCause() throws SQLException {
    SQLException cause = new SQLException("boom");
    when(delegate.executeUpdate()).thenThrow(cause);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    assertThatThrownBy(proxy::executeUpdate)
            .isSameAs(cause);

    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "SELECT", SQL_SELECT);
    verify(delegate, times(1)).executeUpdate();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void delegateFailureOnBatchStillRecordsBatchOperation() throws SQLException {
    SQLException cause = new SQLException("batch-fail");
    when(delegate.executeBatch()).thenThrow(cause);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    assertThatThrownBy(proxy::executeBatch)
            .isSameAs(cause);

    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "BATCH");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "BATCH", SQL_SELECT);
    verify(delegate).executeBatch();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void recordingInvocationHandlesNullArgsArrayGracefully() throws Throwable {
    Method executeMethod = PreparedStatement.class.getMethod("execute");
    when(delegate.execute()).thenReturn(true);

    String insertSql = "insert into t values (?)";
    PreparedStatement proxy = newProxy(insertSql);
    PreparedStatementInvocationHandler handler = new PreparedStatementInvocationHandler(
            delegate, insertSql, TARGET, tracker);

    Object result = handler.invoke(proxy, executeMethod, null);

    assertThat(result).isEqualTo(Boolean.TRUE);
    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "INSERT");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "INSERT", insertSql);
    verify(delegate).execute();
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void recordedMethodNamesMatchDocumentedContract() {
    String[] expected = {
        "executeQuery", "executeUpdate", "execute",
        "executeBatch", "executeLargeUpdate", "executeLargeBatch"};
    assertThat(expected).containsExactly(
            "executeQuery",
            "executeUpdate",
            "execute",
            "executeBatch",
            "executeLargeUpdate",
            "executeLargeBatch");
    verifyNoInteractions(tracker, delegate);
  }

  @Test
  void unknownSqlTokenWithArgsSearchesStringArgument() throws SQLException {
    String adHoc = "  \t update accounts set balance = ?";
    when(delegate.executeUpdate(adHoc)).thenReturn(1);

    PreparedStatement proxy = newProxy(null);
    proxy.executeUpdate(adHoc);

    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "UPDATE");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "UPDATE", adHoc);
    verify(delegate).executeUpdate(adHoc);
    verifyNoMoreInteractions(tracker, delegate);
  }

  @Test
  void nullSqlAndNoStringArgsFallsBackToUnknownOperation() throws SQLException {
    when(delegate.executeUpdate()).thenReturn(1);

    PreparedStatement proxy = newProxy(null);
    proxy.executeUpdate();

    verify(tracker).findMock(ExternalCallTracker.TYPE_DATABASE, TARGET, "UNKNOWN");
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, TARGET, "UNKNOWN", null);
    verify(delegate).executeUpdate();
    verifyNoMoreInteractions(tracker, delegate);
  }
}
