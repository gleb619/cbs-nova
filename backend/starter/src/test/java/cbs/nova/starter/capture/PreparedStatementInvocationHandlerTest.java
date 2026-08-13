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

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
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
 * a mocked {@link ExternalCallRecorder} so the handler is exercised without any DB or Spring
 * context. Verifies SQL extraction, op classification, record-before-delegate ordering, and
 * pass-through behaviour for non-recorded methods.
 */
class PreparedStatementInvocationHandlerTest {

  private static final String TARGET = "jdbc:h2:mem:test";
  private static final String SQL_SELECT = "select id from orders where id = ?";

  private ExternalCallRecorder externalCallRecorder;
  private PreparedStatement delegate;

  @BeforeEach
  void setUp() {
    externalCallRecorder = mock(ExternalCallRecorder.class);
    delegate = mock(PreparedStatement.class);
  }

  private PreparedStatement newProxy(String sql) {
    PreparedStatementInvocationHandler handler = new PreparedStatementInvocationHandler(
            delegate, sql, TARGET, externalCallRecorder);
    return (PreparedStatement) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{PreparedStatement.class, Statement.class},
            handler);
  }

  private Statement newStatementProxy(String sql) {
    PreparedStatementInvocationHandler handler = new PreparedStatementInvocationHandler(
            delegate, sql, TARGET, externalCallRecorder);
    return (Statement) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{Statement.class},
            handler);
  }

  @Test
  void executeQueryForwardsAndRecordsWithFactorySql() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(delegate.executeQuery()).thenReturn(rs);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    ResultSet actual = proxy.executeQuery();

    assertThat(actual).isSameAs(rs);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "SELECT",
            SQL_SELECT);
    verify(delegate).executeQuery();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void executeUpdateForwardsAndRecordsWithFactorySql() throws SQLException {
    when(delegate.executeUpdate()).thenReturn(7);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    int updated = proxy.executeUpdate();

    assertThat(updated).isEqualTo(7);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "SELECT",
            SQL_SELECT);
    verify(delegate).executeUpdate();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void executeForwardsAndRecordsWithFactorySql() throws SQLException {
    when(delegate.execute()).thenReturn(true);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    boolean executed = proxy.execute();

    assertThat(executed).isTrue();
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "SELECT",
            SQL_SELECT);
    verify(delegate).execute();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void executeBatchForwardsAndRecordsBatchOperation() throws SQLException {
    int[] counts = {1, 2};
    when(delegate.executeBatch()).thenReturn(counts);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    int[] actual = proxy.executeBatch();

    assertThat(actual).isSameAs(counts);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "BATCH",
            SQL_SELECT);
    verify(delegate).executeBatch();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void executeLargeUpdateForwardsAndRecordsWithFactorySql() throws SQLException {
    when(delegate.executeLargeUpdate()).thenReturn(42L);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    long updated = proxy.executeLargeUpdate();

    assertThat(updated).isEqualTo(42L);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "SELECT",
            SQL_SELECT);
    verify(delegate).executeLargeUpdate();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void executeLargeBatchForwardsAndRecordsBatchOperation() throws SQLException {
    long[] counts = {10L, 20L};
    when(delegate.executeLargeBatch()).thenReturn(counts);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    long[] actual = proxy.executeLargeBatch();

    assertThat(actual).isSameAs(counts);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "BATCH",
            SQL_SELECT);
    verify(delegate).executeLargeBatch();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void operationIsFirstUppercaseSqlToken() throws SQLException {
    when(delegate.execute()).thenReturn(false);
    String mixedCaseSql = "  \tInsert Into foo values (?)";

    PreparedStatement proxy = newProxy(mixedCaseSql);
    proxy.execute();

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            mixedCaseSql);
    verify(delegate).execute();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteWithNoFactorySqlUsesArgument() throws SQLException {
    String adHocSql = "delete from orders where id = 1";
    when(delegate.execute(adHocSql)).thenReturn(false);

    PreparedStatement proxy = newProxy(null);
    boolean executed = proxy.execute(adHocSql);

    assertThat(executed).isFalse();
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "DELETE",
            adHocSql);
    verify(delegate).execute(adHocSql);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void factorySqlTakesPrecedenceOverArgumentSql() throws SQLException {
    String argSql = "select 1 from dual";
    when(delegate.executeUpdate(argSql)).thenReturn(3);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    int updated = proxy.executeUpdate(argSql);

    assertThat(updated).isEqualTo(3);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "SELECT",
            SQL_SELECT);
    verify(delegate).executeUpdate(argSql);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void unknownSqlTokenIsUsedWhenSqlMissing() throws SQLException {
    when(delegate.executeQuery()).thenReturn(mock(ResultSet.class));

    PreparedStatement proxy = newProxy(null);
    proxy.executeQuery();

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "UNKNOWN",
            null);
    verify(delegate).executeQuery();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void emptySqlFallsBackToUnknownOperation() throws SQLException {
    when(delegate.executeQuery()).thenReturn(mock(ResultSet.class));

    PreparedStatement proxy = newProxy("   ");
    proxy.executeQuery();

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "UNKNOWN",
            "   ");
    verify(delegate).executeQuery();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void batchOperationIgnoresStringArguments() throws SQLException {
    int[] counts = {5};
    when(delegate.executeBatch()).thenReturn(counts);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    proxy.executeBatch();

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "BATCH",
            SQL_SELECT);
    verify(delegate).executeBatch();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void nonRecordedMethodsForwardAndDoNotTouchRecorder() throws SQLException {
    PreparedStatement proxy = newProxy(SQL_SELECT);
    proxy.setString(1, "abc");
    proxy.close();

    verify(delegate).setString(1, "abc");
    verify(delegate).close();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getterMethodsForwardAndDoNotTouchRecorder() throws SQLException {
    when(delegate.getResultSet()).thenReturn(mock(ResultSet.class));
    when(delegate.getUpdateCount()).thenReturn(11);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    ResultSet rs = proxy.getResultSet();
    int count = proxy.getUpdateCount();

    assertThat(rs).isNotNull();
    assertThat(count).isEqualTo(11);
    verify(delegate).getResultSet();
    verify(delegate).getUpdateCount();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void recordHappensBeforeDelegateInvocation() throws SQLException {
    when(delegate.executeUpdate()).thenReturn(1);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    proxy.executeUpdate();

    InOrder ordered = inOrder(externalCallRecorder, delegate);
    ordered.verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET,
            "SELECT",
            SQL_SELECT);
    ordered.verify(delegate).executeUpdate();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void delegateFailureStillRecordsAndSurfacesOriginalCause() throws SQLException {
    SQLException cause = new SQLException("boom");
    when(delegate.executeUpdate()).thenThrow(cause);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    assertThatThrownBy(proxy::executeUpdate)
            .isSameAs(cause);

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "SELECT",
            SQL_SELECT);
    verify(delegate, times(1)).executeUpdate();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void delegateFailureOnBatchStillRecordsBatchOperation() throws SQLException {
    SQLException cause = new SQLException("batch-fail");
    when(delegate.executeBatch()).thenThrow(cause);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    assertThatThrownBy(proxy::executeBatch)
            .isSameAs(cause);

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "BATCH",
            SQL_SELECT);
    verify(delegate).executeBatch();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void recordingInvocationHandlesNullArgsArrayGracefully() throws Throwable {
    Method executeMethod = PreparedStatement.class.getMethod("execute");
    when(delegate.execute()).thenReturn(true);

    String insertSql = "insert into t values (?)";
    PreparedStatement proxy = newProxy(insertSql);
    PreparedStatementInvocationHandler handler = new PreparedStatementInvocationHandler(
            delegate, insertSql, TARGET, externalCallRecorder);

    Object result = handler.invoke(proxy, executeMethod, null);

    assertThat(result).isEqualTo(Boolean.TRUE);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            insertSql);
    verify(delegate).execute();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
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
    verifyNoInteractions(externalCallRecorder, delegate);
  }

  @Test
  void unknownSqlTokenWithArgsSearchesStringArgument() throws SQLException {
    String adHoc = "  \t update accounts set balance = ?";
    when(delegate.executeUpdate(adHoc)).thenReturn(1);

    PreparedStatement proxy = newProxy(null);
    proxy.executeUpdate(adHoc);

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "UPDATE",
            adHoc);
    verify(delegate).executeUpdate(adHoc);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void nullSqlAndNoStringArgsFallsBackToUnknownOperation() throws SQLException {
    when(delegate.executeUpdate()).thenReturn(1);

    PreparedStatement proxy = newProxy(null);
    proxy.executeUpdate();

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "UNKNOWN",
            null);
    verify(delegate).executeUpdate();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void proxyIdentityMethodsReturnConsistentValues() {
    PreparedStatement proxy = newProxy(SQL_SELECT);
    PreparedStatement otherProxy = newProxy(SQL_SELECT);

    assertThat(proxy.equals(proxy)).isTrue();
    assertThat(proxy.equals(delegate)).isFalse();
    assertThat(proxy.equals(otherProxy)).isFalse();
    assertThat(proxy.hashCode()).isEqualTo(System.identityHashCode(proxy));
    assertThat(proxy.toString()).contains("StatementProxy");
  }

  @Test
  void wrapperMethodsForwardToDelegate() throws SQLException {
    when(delegate.isWrapperFor(Statement.class)).thenReturn(true);
    when(delegate.unwrap(Statement.class)).thenReturn(delegate);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    boolean wrapper = proxy.isWrapperFor(Statement.class);
    Statement unwrapped = proxy.unwrap(Statement.class);

    assertThat(wrapper).isTrue();
    assertThat(unwrapped).isSameAs(delegate);
    verify(delegate).isWrapperFor(Statement.class);
    verify(delegate).unwrap(Statement.class);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void isClosedForwardsWithoutRecording() throws SQLException {
    when(delegate.isClosed()).thenReturn(true);

    PreparedStatement proxy = newProxy(SQL_SELECT);
    boolean closed = proxy.isClosed();

    assertThat(closed).isTrue();
    verify(delegate).isClosed();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void rawStatementExecuteUpdateStringRecordsArgumentSql() throws SQLException {
    String adHoc = "update orders set status = 'done'";
    when(delegate.executeUpdate(adHoc)).thenReturn(4);

    Statement proxy = newStatementProxy(null);
    int updated = proxy.executeUpdate(adHoc);

    assertThat(updated).isEqualTo(4);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "UPDATE",
            adHoc);
    verify(delegate).executeUpdate(adHoc);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteUpdateStringWithAutoGeneratedKeysRecordsArgumentSql()
          throws SQLException {
    String adHoc = "insert into orders (name) values ('x')";
    when(delegate.executeUpdate(adHoc, Statement.RETURN_GENERATED_KEYS)).thenReturn(1);

    Statement proxy = newStatementProxy(null);
    int updated = proxy.executeUpdate(adHoc, Statement.RETURN_GENERATED_KEYS);

    assertThat(updated).isEqualTo(1);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            adHoc);
    verify(delegate).executeUpdate(adHoc, Statement.RETURN_GENERATED_KEYS);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteUpdateStringWithColumnIndexesRecordsArgumentSql() throws SQLException {
    String adHoc = "insert into orders (name) values ('y')";
    int[] keys = {1};
    when(delegate.executeUpdate(adHoc, keys)).thenReturn(1);

    Statement proxy = newStatementProxy(null);
    int updated = proxy.executeUpdate(adHoc, keys);

    assertThat(updated).isEqualTo(1);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            adHoc);
    verify(delegate).executeUpdate(adHoc, keys);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteUpdateStringWithColumnNamesRecordsArgumentSql() throws SQLException {
    String adHoc = "insert into orders (name) values ('z')";
    String[] columns = {"id"};
    when(delegate.executeUpdate(adHoc, columns)).thenReturn(1);

    Statement proxy = newStatementProxy(null);
    int updated = proxy.executeUpdate(adHoc, columns);

    assertThat(updated).isEqualTo(1);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            adHoc);
    verify(delegate).executeUpdate(adHoc, columns);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteStringRecordsArgumentSql() throws SQLException {
    String adHoc = "select count(*) from orders";
    when(delegate.execute(adHoc)).thenReturn(true);

    Statement proxy = newStatementProxy(null);
    boolean executed = proxy.execute(adHoc);

    assertThat(executed).isTrue();
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "SELECT",
            adHoc);
    verify(delegate).execute(adHoc);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteStringWithAutoGeneratedKeysRecordsArgumentSql() throws SQLException {
    String adHoc = "insert into orders (name) values ('w')";
    when(delegate.execute(adHoc, Statement.RETURN_GENERATED_KEYS)).thenReturn(true);

    Statement proxy = newStatementProxy(null);
    boolean executed = proxy.execute(adHoc, Statement.RETURN_GENERATED_KEYS);

    assertThat(executed).isTrue();
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            adHoc);
    verify(delegate).execute(adHoc, Statement.RETURN_GENERATED_KEYS);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteStringWithColumnIndexesRecordsArgumentSql() throws SQLException {
    String adHoc = "insert into orders (name) values ('v')";
    int[] keys = {1};
    when(delegate.execute(adHoc, keys)).thenReturn(true);

    Statement proxy = newStatementProxy(null);
    boolean executed = proxy.execute(adHoc, keys);

    assertThat(executed).isTrue();
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            adHoc);
    verify(delegate).execute(adHoc, keys);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteStringWithColumnNamesRecordsArgumentSql() throws SQLException {
    String adHoc = "insert into orders (name) values ('u')";
    String[] columns = {"id"};
    when(delegate.execute(adHoc, columns)).thenReturn(true);

    Statement proxy = newStatementProxy(null);
    boolean executed = proxy.execute(adHoc, columns);

    assertThat(executed).isTrue();
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            adHoc);
    verify(delegate).execute(adHoc, columns);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteLargeUpdateStringRecordsArgumentSql() throws SQLException {
    String adHoc = "update orders set count = count + 1";
    when(delegate.executeLargeUpdate(adHoc)).thenReturn(8L);

    Statement proxy = newStatementProxy(null);
    long updated = proxy.executeLargeUpdate(adHoc);

    assertThat(updated).isEqualTo(8L);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "UPDATE",
            adHoc);
    verify(delegate).executeLargeUpdate(adHoc);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteLargeUpdateStringWithAutoGeneratedKeysRecordsArgumentSql()
          throws SQLException {
    String adHoc = "insert into orders (name) values ('t')";
    when(delegate.executeLargeUpdate(adHoc, Statement.RETURN_GENERATED_KEYS)).thenReturn(1L);

    Statement proxy = newStatementProxy(null);
    long updated = proxy.executeLargeUpdate(adHoc, Statement.RETURN_GENERATED_KEYS);

    assertThat(updated).isEqualTo(1L);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            adHoc);
    verify(delegate).executeLargeUpdate(adHoc, Statement.RETURN_GENERATED_KEYS);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteLargeUpdateStringWithColumnIndexesRecordsArgumentSql()
          throws SQLException {
    String adHoc = "insert into orders (name) values ('s')";
    int[] keys = {1};
    when(delegate.executeLargeUpdate(adHoc, keys)).thenReturn(1L);

    Statement proxy = newStatementProxy(null);
    long updated = proxy.executeLargeUpdate(adHoc, keys);

    assertThat(updated).isEqualTo(1L);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            adHoc);
    verify(delegate).executeLargeUpdate(adHoc, keys);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteLargeUpdateStringWithColumnNamesRecordsArgumentSql()
          throws SQLException {
    String adHoc = "insert into orders (name) values ('r')";
    String[] columns = {"id"};
    when(delegate.executeLargeUpdate(adHoc, columns)).thenReturn(1L);

    Statement proxy = newStatementProxy(null);
    long updated = proxy.executeLargeUpdate(adHoc, columns);

    assertThat(updated).isEqualTo(1L);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            adHoc);
    verify(delegate).executeLargeUpdate(adHoc, columns);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteBatchRecordsBatchOperation() throws SQLException {
    int[] counts = {2, 3};
    when(delegate.executeBatch()).thenReturn(counts);

    Statement proxy = newStatementProxy(null);
    int[] actual = proxy.executeBatch();

    assertThat(actual).isSameAs(counts);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "BATCH",
            null);
    verify(delegate).executeBatch();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteLargeBatchRecordsBatchOperation() throws SQLException {
    long[] counts = {5L, 6L};
    when(delegate.executeLargeBatch()).thenReturn(counts);

    Statement proxy = newStatementProxy(null);
    long[] actual = proxy.executeLargeBatch();

    assertThat(actual).isSameAs(counts);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "BATCH",
            null);
    verify(delegate).executeLargeBatch();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }
}
