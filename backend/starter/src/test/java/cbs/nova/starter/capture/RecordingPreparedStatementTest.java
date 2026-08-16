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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Pure-unit tests for {@link RecordingPreparedStatement} and {@link RecordingStatement}. Uses
 * Mockito-mocked delegates and a mocked {@link ExternalCallRecorder} so the decorators are
 * exercised without any DB or Spring context. Verifies SQL extraction, op classification,
 * record-before-delegate ordering, and pass-through behaviour for non-recorded methods.
 */
class RecordingPreparedStatementTest {

  private static final String TARGET = "jdbc:h2:mem:test";
  private static final String SQL_SELECT = "select id from orders where id = ?";

  private ExternalCallRecorder externalCallRecorder;
  private PreparedStatement delegate;

  @BeforeEach
  void setUp() {
    externalCallRecorder = mock(ExternalCallRecorder.class);
    delegate = mock(PreparedStatement.class);
  }

  private PreparedStatement newPreparedStatement(String sql) {
    return new RecordingPreparedStatement(delegate, sql, TARGET, externalCallRecorder);
  }

  private Statement newStatement(String sql) {
    return new RecordingStatement(delegate, sql, TARGET, externalCallRecorder);
  }

  @Test
  void executeQueryForwardsAndRecordsWithFactorySql() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(delegate.executeQuery()).thenReturn(rs);

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    ResultSet actual = statement.executeQuery();

    assertThat(actual).isSameAs(rs);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "SELECT",
            SQL_SELECT);
    verify(delegate).executeQuery();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void executeUpdateForwardsAndRecordsWithFactorySql() throws SQLException {
    when(delegate.executeUpdate()).thenReturn(7);

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    int updated = statement.executeUpdate();

    assertThat(updated).isEqualTo(7);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "SELECT",
            SQL_SELECT);
    verify(delegate).executeUpdate();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void executeForwardsAndRecordsWithFactorySql() throws SQLException {
    when(delegate.execute()).thenReturn(true);

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    boolean executed = statement.execute();

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

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    int[] actual = statement.executeBatch();

    assertThat(actual).isSameAs(counts);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "BATCH",
            SQL_SELECT);
    verify(delegate).executeBatch();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void executeLargeUpdateForwardsAndRecordsWithFactorySql() throws SQLException {
    when(delegate.executeLargeUpdate()).thenReturn(42L);

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    long updated = statement.executeLargeUpdate();

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

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    long[] actual = statement.executeLargeBatch();

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

    PreparedStatement statement = newPreparedStatement(mixedCaseSql);
    statement.execute();

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            mixedCaseSql);
    verify(delegate).execute();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteWithNoFactorySqlUsesArgument() throws SQLException {
    String adHocSql = "delete from orders where id = 1";
    when(delegate.execute(adHocSql)).thenReturn(false);

    Statement statement = newStatement(null);
    boolean executed = statement.execute(adHocSql);

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

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    int updated = statement.executeUpdate(argSql);

    assertThat(updated).isEqualTo(3);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "SELECT",
            SQL_SELECT);
    verify(delegate).executeUpdate(argSql);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void unknownSqlTokenIsUsedWhenSqlMissing() throws SQLException {
    when(delegate.executeQuery()).thenReturn(mock(ResultSet.class));

    PreparedStatement statement = newPreparedStatement(null);
    statement.executeQuery();

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "UNKNOWN",
            null);
    verify(delegate).executeQuery();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void emptySqlFallsBackToUnknownOperation() throws SQLException {
    when(delegate.executeQuery()).thenReturn(mock(ResultSet.class));

    PreparedStatement statement = newPreparedStatement("   ");
    statement.executeQuery();

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "UNKNOWN",
            "   ");
    verify(delegate).executeQuery();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void nonRecordedMethodsForwardAndDoNotTouchRecorder() throws SQLException {
    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    statement.setString(1, "abc");
    statement.close();

    verify(delegate).setString(1, "abc");
    verify(delegate).close();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getterMethodsForwardAndDoNotTouchRecorder() throws SQLException {
    when(delegate.getResultSet()).thenReturn(mock(ResultSet.class));
    when(delegate.getUpdateCount()).thenReturn(11);

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    ResultSet rs = statement.getResultSet();
    int count = statement.getUpdateCount();

    assertThat(rs).isNotNull();
    assertThat(count).isEqualTo(11);
    verify(delegate).getResultSet();
    verify(delegate).getUpdateCount();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void recordHappensBeforeDelegateInvocation() throws SQLException {
    when(delegate.executeUpdate()).thenReturn(1);

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    statement.executeUpdate();

    InOrder ordered = inOrder(externalCallRecorder, delegate);
    ordered.verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET,
            "SELECT", SQL_SELECT);
    ordered.verify(delegate).executeUpdate();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void delegateFailureStillRecordsAndSurfacesOriginalCause() throws SQLException {
    SQLException cause = new SQLException("boom");
    when(delegate.executeUpdate()).thenThrow(cause);

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    assertThatThrownBy(statement::executeUpdate)
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

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    assertThatThrownBy(statement::executeBatch)
            .isSameAs(cause);

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "BATCH",
            SQL_SELECT);
    verify(delegate).executeBatch();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void decoratorIdentityMethodsReturnConsistentValues() {
    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    PreparedStatement otherStatement = newPreparedStatement(SQL_SELECT);

    assertThat(statement.equals(statement)).isTrue();
    assertThat(statement.equals(delegate)).isFalse();
    assertThat(statement.equals(otherStatement)).isFalse();
    assertThat(statement.hashCode()).isEqualTo(System.identityHashCode(statement));
    assertThat(statement.toString()).contains("RecordingPreparedStatement");
  }

  @Test
  void wrapperMethodsForwardToDelegate() throws SQLException {
    when(delegate.isWrapperFor(Statement.class)).thenReturn(true);
    when(delegate.unwrap(Statement.class)).thenReturn(delegate);

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    boolean wrapper = statement.isWrapperFor(Statement.class);
    Statement unwrapped = statement.unwrap(Statement.class);

    assertThat(wrapper).isTrue();
    assertThat(unwrapped).isSameAs(delegate);
    verify(delegate).isWrapperFor(Statement.class);
    verify(delegate).unwrap(Statement.class);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void isClosedForwardsWithoutRecording() throws SQLException {
    when(delegate.isClosed()).thenReturn(true);

    PreparedStatement statement = newPreparedStatement(SQL_SELECT);
    boolean closed = statement.isClosed();

    assertThat(closed).isTrue();
    verify(delegate).isClosed();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void rawStatementExecuteUpdateStringRecordsArgumentSql() throws SQLException {
    String adHoc = "update orders set status = 'done'";
    when(delegate.executeUpdate(adHoc)).thenReturn(4);

    Statement statement = newStatement(null);
    int updated = statement.executeUpdate(adHoc);

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

    Statement statement = newStatement(null);
    int updated = statement.executeUpdate(adHoc, Statement.RETURN_GENERATED_KEYS);

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

    Statement statement = newStatement(null);
    int updated = statement.executeUpdate(adHoc, keys);

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

    Statement statement = newStatement(null);
    int updated = statement.executeUpdate(adHoc, columns);

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

    Statement statement = newStatement(null);
    boolean executed = statement.execute(adHoc);

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

    Statement statement = newStatement(null);
    boolean executed = statement.execute(adHoc, Statement.RETURN_GENERATED_KEYS);

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

    Statement statement = newStatement(null);
    boolean executed = statement.execute(adHoc, keys);

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

    Statement statement = newStatement(null);
    boolean executed = statement.execute(adHoc, columns);

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

    Statement statement = newStatement(null);
    long updated = statement.executeLargeUpdate(adHoc);

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

    Statement statement = newStatement(null);
    long updated = statement.executeLargeUpdate(adHoc, Statement.RETURN_GENERATED_KEYS);

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

    Statement statement = newStatement(null);
    long updated = statement.executeLargeUpdate(adHoc, keys);

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

    Statement statement = newStatement(null);
    long updated = statement.executeLargeUpdate(adHoc, columns);

    assertThat(updated).isEqualTo(1L);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            adHoc);
    verify(delegate).executeLargeUpdate(adHoc, columns);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteQueryStringRecordsArgumentSql() throws SQLException {
    String adHoc = "  \t select 1";
    ResultSet rs = mock(ResultSet.class);
    when(delegate.executeQuery(adHoc)).thenReturn(rs);

    Statement statement = newStatement(null);
    ResultSet actual = statement.executeQuery(adHoc);

    assertThat(actual).isSameAs(rs);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "SELECT",
            adHoc);
    verify(delegate).executeQuery(adHoc);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteBatchRecordsBatchOperation() throws SQLException {
    int[] counts = {2, 3};
    when(delegate.executeBatch()).thenReturn(counts);

    Statement statement = newStatement(null);
    int[] actual = statement.executeBatch();

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

    Statement statement = newStatement(null);
    long[] actual = statement.executeLargeBatch();

    assertThat(actual).isSameAs(counts);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "BATCH",
            null);
    verify(delegate).executeLargeBatch();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }
}
