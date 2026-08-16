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

import java.sql.CallableStatement;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Calendar;

/**
 * Pure-unit tests for {@link RecordingCallableStatement}. Uses a Mockito-mocked delegate and a
 * mocked {@link ExternalCallRecorder} so the decorator is exercised without any DB or Spring
 * context.
 */
class RecordingCallableStatementTest {

  private static final String TARGET = "jdbc:h2:mem:test";
  private static final String SQL_CALL = "call get_order(?, ?)";
  private static final String SQL_SELECT = "select id from orders where id = ?";
  private static final String SQL_UPDATE = "update orders set status = 'done'";

  private ExternalCallRecorder externalCallRecorder;
  private CallableStatement delegate;

  @BeforeEach
  void setUp() {
    externalCallRecorder = mock(ExternalCallRecorder.class);
    delegate = mock(CallableStatement.class);
  }

  private CallableStatement newCallableStatement(String sql) {
    return new RecordingCallableStatement(delegate, sql, TARGET, externalCallRecorder);
  }

  @Test
  void executeQueryForwardsAndRecordsWithFactorySql() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(delegate.executeQuery()).thenReturn(rs);

    CallableStatement statement = newCallableStatement(SQL_SELECT);
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

    CallableStatement statement = newCallableStatement(SQL_UPDATE);
    int updated = statement.executeUpdate();

    assertThat(updated).isEqualTo(7);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "UPDATE",
            SQL_UPDATE);
    verify(delegate).executeUpdate();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void executeForwardsAndRecordsWithFactorySql() throws SQLException {
    when(delegate.execute()).thenReturn(true);

    CallableStatement statement = newCallableStatement(SQL_CALL);
    boolean executed = statement.execute();

    assertThat(executed).isTrue();
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "CALL",
            SQL_CALL);
    verify(delegate).execute();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void executeBatchForwardsAndRecordsBatchOperation() throws SQLException {
    int[] counts = {1, 2};
    when(delegate.executeBatch()).thenReturn(counts);

    CallableStatement statement = newCallableStatement(SQL_CALL);
    int[] actual = statement.executeBatch();

    assertThat(actual).isSameAs(counts);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "BATCH",
            SQL_CALL);
    verify(delegate).executeBatch();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void executeLargeUpdateForwardsAndRecordsWithFactorySql() throws SQLException {
    when(delegate.executeLargeUpdate()).thenReturn(42L);

    CallableStatement statement = newCallableStatement(SQL_UPDATE);
    long updated = statement.executeLargeUpdate();

    assertThat(updated).isEqualTo(42L);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "UPDATE",
            SQL_UPDATE);
    verify(delegate).executeLargeUpdate();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void executeLargeBatchForwardsAndRecordsBatchOperation() throws SQLException {
    long[] counts = {10L, 20L};
    when(delegate.executeLargeBatch()).thenReturn(counts);

    CallableStatement statement = newCallableStatement(SQL_CALL);
    long[] actual = statement.executeLargeBatch();

    assertThat(actual).isSameAs(counts);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "BATCH",
            SQL_CALL);
    verify(delegate).executeLargeBatch();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void rawStatementExecuteUsesArgumentSql() throws SQLException {
    String adHoc = "delete from orders where id = 1";
    when(delegate.execute(adHoc)).thenReturn(false);

    CallableStatement statement = newCallableStatement(null);
    boolean executed = statement.execute(adHoc);

    assertThat(executed).isFalse();
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "DELETE",
            adHoc);
    verify(delegate).execute(adHoc);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void factorySqlTakesPrecedenceOverArgumentSql() throws SQLException {
    String argSql = "select 1 from dual";
    when(delegate.executeUpdate(argSql)).thenReturn(3);

    CallableStatement statement = newCallableStatement(SQL_UPDATE);
    int updated = statement.executeUpdate(argSql);

    assertThat(updated).isEqualTo(3);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "UPDATE",
            SQL_UPDATE);
    verify(delegate).executeUpdate(argSql);
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void operationIsFirstUppercaseSqlToken() throws SQLException {
    when(delegate.execute()).thenReturn(false);
    String mixedCaseSql = "  \tInsert Into foo values (?)";

    CallableStatement statement = newCallableStatement(mixedCaseSql);
    statement.execute();

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "INSERT",
            mixedCaseSql);
    verify(delegate).execute();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void recordHappensBeforeDelegateInvocation() throws SQLException {
    when(delegate.executeUpdate()).thenReturn(1);

    CallableStatement statement = newCallableStatement(SQL_UPDATE);
    statement.executeUpdate();

    InOrder ordered = inOrder(externalCallRecorder, delegate);
    ordered.verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET,
            "UPDATE", SQL_UPDATE);
    ordered.verify(delegate).executeUpdate();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void delegateFailureStillRecordsAndSurfacesOriginalCause() throws SQLException {
    SQLException cause = new SQLException("boom");
    when(delegate.executeUpdate()).thenThrow(cause);

    CallableStatement statement = newCallableStatement(SQL_UPDATE);
    assertThatThrownBy(statement::executeUpdate)
            .isSameAs(cause);

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "UPDATE",
            SQL_UPDATE);
    verify(delegate, times(1)).executeUpdate();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void delegateFailureOnBatchStillRecordsBatchOperation() throws SQLException {
    SQLException cause = new SQLException("batch-fail");
    when(delegate.executeBatch()).thenThrow(cause);

    CallableStatement statement = newCallableStatement(SQL_CALL);
    assertThatThrownBy(statement::executeBatch)
            .isSameAs(cause);

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, TARGET, "BATCH",
            SQL_CALL);
    verify(delegate).executeBatch();
    verifyNoMoreInteractions(externalCallRecorder, delegate);
  }

  @Test
  void nonRecordedCallableStatementMethodsForwardAndDoNotTouchRecorder() throws SQLException {
    CallableStatement statement = newCallableStatement(SQL_CALL);
    statement.registerOutParameter(1, Types.INTEGER);
    statement.registerOutParameter(2, Types.VARCHAR, "VARCHAR");
    statement.setString(1, "abc");
    statement.close();

    verify(delegate).registerOutParameter(1, Types.INTEGER);
    verify(delegate).registerOutParameter(2, Types.VARCHAR, "VARCHAR");
    verify(delegate).setString(1, "abc");
    verify(delegate).close();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void registerOutParameterByIndexForwardsWithoutRecording() throws SQLException {
    CallableStatement statement = newCallableStatement(SQL_CALL);
    statement.registerOutParameter(1, Types.INTEGER);
    statement.registerOutParameter(2, Types.NUMERIC, 2);
    statement.registerOutParameter(3, Types.STRUCT, "TYPE_NAME");

    verify(delegate).registerOutParameter(1, Types.INTEGER);
    verify(delegate).registerOutParameter(2, Types.NUMERIC, 2);
    verify(delegate).registerOutParameter(3, Types.STRUCT, "TYPE_NAME");
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void registerOutParameterByNameForwardsWithoutRecording() throws SQLException {
    CallableStatement statement = newCallableStatement(SQL_CALL);
    statement.registerOutParameter("id", Types.INTEGER);
    statement.registerOutParameter("amount", Types.NUMERIC, 2);
    statement.registerOutParameter("record", Types.STRUCT, "TYPE_NAME");

    verify(delegate).registerOutParameter("id", Types.INTEGER);
    verify(delegate).registerOutParameter("amount", Types.NUMERIC, 2);
    verify(delegate).registerOutParameter("record", Types.STRUCT, "TYPE_NAME");
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getStringByIndexForwardsWithoutRecording() throws SQLException {
    when(delegate.getString(1)).thenReturn("value");

    CallableStatement statement = newCallableStatement(SQL_CALL);
    String actual = statement.getString(1);

    assertThat(actual).isEqualTo("value");
    verify(delegate).getString(1);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getStringByNameForwardsWithoutRecording() throws SQLException {
    when(delegate.getString("name")).thenReturn("value");

    CallableStatement statement = newCallableStatement(SQL_CALL);
    String actual = statement.getString("name");

    assertThat(actual).isEqualTo("value");
    verify(delegate).getString("name");
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getObjectByIndexForwardsWithoutRecording() throws SQLException {
    Object value = new Object();
    when(delegate.getObject(1)).thenReturn(value);

    CallableStatement statement = newCallableStatement(SQL_CALL);
    Object actual = statement.getObject(1);

    assertThat(actual).isSameAs(value);
    verify(delegate).getObject(1);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getObjectByNameForwardsWithoutRecording() throws SQLException {
    Object value = new Object();
    when(delegate.getObject("obj")).thenReturn(value);

    CallableStatement statement = newCallableStatement(SQL_CALL);
    Object actual = statement.getObject("obj");

    assertThat(actual).isSameAs(value);
    verify(delegate).getObject("obj");
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getDateByIndexForwardsWithoutRecording() throws SQLException {
    Date date = new Date(0L);
    when(delegate.getDate(1)).thenReturn(date);

    CallableStatement statement = newCallableStatement(SQL_CALL);
    Date actual = statement.getDate(1);

    assertThat(actual).isSameAs(date);
    verify(delegate).getDate(1);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getDateByNameForwardsWithoutRecording() throws SQLException {
    Date date = new Date(0L);
    when(delegate.getDate("date")).thenReturn(date);

    CallableStatement statement = newCallableStatement(SQL_CALL);
    Date actual = statement.getDate("date");

    assertThat(actual).isSameAs(date);
    verify(delegate).getDate("date");
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getDateByIndexWithCalendarForwardsWithoutRecording() throws SQLException {
    Date date = new Date(0L);
    Calendar calendar = Calendar.getInstance();
    when(delegate.getDate(1, calendar)).thenReturn(date);

    CallableStatement statement = newCallableStatement(SQL_CALL);
    Date actual = statement.getDate(1, calendar);

    assertThat(actual).isSameAs(date);
    verify(delegate).getDate(1, calendar);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getDateByNameWithCalendarForwardsWithoutRecording() throws SQLException {
    Date date = new Date(0L);
    Calendar calendar = Calendar.getInstance();
    when(delegate.getDate("date", calendar)).thenReturn(date);

    CallableStatement statement = newCallableStatement(SQL_CALL);
    Date actual = statement.getDate("date", calendar);

    assertThat(actual).isSameAs(date);
    verify(delegate).getDate("date", calendar);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void isClosedForwardsWithoutRecording() throws SQLException {
    when(delegate.isClosed()).thenReturn(true);

    CallableStatement statement = newCallableStatement(SQL_CALL);
    boolean closed = statement.isClosed();

    assertThat(closed).isTrue();
    verify(delegate).isClosed();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void wrapperMethodsForwardToDelegate() throws SQLException {
    when(delegate.isWrapperFor(Statement.class)).thenReturn(true);
    when(delegate.unwrap(Statement.class)).thenReturn(delegate);

    CallableStatement statement = newCallableStatement(SQL_CALL);
    boolean wrapper = statement.isWrapperFor(Statement.class);
    Statement unwrapped = statement.unwrap(Statement.class);

    assertThat(wrapper).isTrue();
    assertThat(unwrapped).isSameAs(delegate);
    verify(delegate).isWrapperFor(Statement.class);
    verify(delegate).unwrap(Statement.class);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void decoratorIdentityMethodsReturnConsistentValues() {
    CallableStatement statement = newCallableStatement(SQL_CALL);
    CallableStatement otherStatement = newCallableStatement(SQL_CALL);

    assertThat(statement.equals(statement)).isTrue();
    assertThat(statement.equals(delegate)).isFalse();
    assertThat(statement.equals(otherStatement)).isFalse();
    assertThat(statement.hashCode()).isEqualTo(System.identityHashCode(statement));
    assertThat(statement.toString()).contains("RecordingCallableStatement");
  }
}
