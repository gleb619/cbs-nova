package cbs.nova.starter.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Pure-unit tests for {@link RecordingConnection}. Wraps a Mockito-mocked {@link Connection} and
 * verifies the statement-factory wrapping contract: returned statements are wrapped in the correct
 * decorator class and recorded calls flow to the {@link ExternalCallRecorder} using the factory
 * SQL.
 */
class RecordingConnectionTest {

  private static final String URL = "jdbc:h2:mem:test";
  private static final String SELECT_SQL = "select id from orders where id = ?";
  private static final String CALL_SQL = "{ call my_proc(?) }";

  private ExternalCallRecorder externalCallRecorder;
  private Connection connection;
  private DatabaseMetaData metaData;
  private PreparedStatement preparedStatement;
  private CallableStatement callableStatement;
  private Statement statement;
  private RecordingConnection recordingConnection;

  @BeforeEach
  void setUp() throws SQLException {
    externalCallRecorder = mock(ExternalCallRecorder.class);
    connection = mock(Connection.class);
    metaData = mock(DatabaseMetaData.class);
    preparedStatement = mock(PreparedStatement.class);
    callableStatement = mock(CallableStatement.class);
    statement = mock(Statement.class);

    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(URL);
    when(connection.prepareStatement(SELECT_SQL)).thenReturn(preparedStatement);
    when(connection.prepareCall(CALL_SQL)).thenReturn(callableStatement);
    when(connection.createStatement()).thenReturn(statement);

    recordingConnection = new RecordingConnection(connection, externalCallRecorder);
  }

  @Test
  void prepareStatementReturnsWrappedStatementWiredToSameRecorder() throws SQLException {
    PreparedStatement wrapped = recordingConnection.prepareStatement(SELECT_SQL);

    assertThat(wrapped).isInstanceOf(RecordingPreparedStatement.class);
    verify(connection).prepareStatement(SELECT_SQL);
    verify(connection).getMetaData();
    verify(metaData).getURL();
    // Statement factory methods themselves do not record — the decorator only forwards.
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void prepareStatementExecuteQueryRecordsFactorySqlAndOperation() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(preparedStatement.executeQuery()).thenReturn(rs);

    PreparedStatement wrapped = recordingConnection.prepareStatement(SELECT_SQL);
    ResultSet actual = wrapped.executeQuery();

    assertThat(actual).isSameAs(rs);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, URL, "SELECT",
            SELECT_SQL);
    verify(preparedStatement).executeQuery();
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void prepareCallReturnsWrappedCallableStatementWiredToSameRecorder() throws SQLException {
    CallableStatement wrapped = recordingConnection.prepareCall(CALL_SQL);

    assertThat(wrapped).isInstanceOf(RecordingCallableStatement.class);
    verify(connection).prepareCall(CALL_SQL);
    verify(connection).getMetaData();
    verify(metaData).getURL();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void prepareCallExecuteRecordsFactorySqlAndOperation() throws SQLException {
    when(callableStatement.execute()).thenReturn(true);

    CallableStatement wrapped = recordingConnection.prepareCall(CALL_SQL);
    boolean executed = wrapped.execute();

    assertThat(executed).isTrue();
    // First whitespace-separated token of "{ call my_proc(?) }" is "{".
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, URL, "{", CALL_SQL);
    verify(callableStatement).execute();
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void createStatementReturnsWrappedStatementWithNoFactorySql() throws SQLException {
    Statement wrapped = recordingConnection.createStatement();

    assertThat(wrapped).isInstanceOf(RecordingStatement.class);
    assertThat(wrapped).isNotInstanceOf(RecordingPreparedStatement.class);
    verify(connection).createStatement();
    verify(connection).getMetaData();
    verify(metaData).getURL();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void createStatementExecuteQueryUsesFirstStringArgAsSql() throws SQLException {
    String adHoc = "delete from orders where id = 1";
    ResultSet rs = mock(ResultSet.class);
    when(statement.executeQuery(adHoc)).thenReturn(rs);

    Statement wrapped = recordingConnection.createStatement();
    ResultSet actual = wrapped.executeQuery(adHoc);

    assertThat(actual).isSameAs(rs);
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, URL, "DELETE", adHoc);
    verify(statement).executeQuery(adHoc);
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void nonStatementFactoryMethodsForwardAndDoNotTouchRecorder() throws SQLException {
    when(connection.getAutoCommit()).thenReturn(false);

    recordingConnection.commit();
    recordingConnection.setAutoCommit(true);
    boolean autoCommit = recordingConnection.getAutoCommit();
    recordingConnection.close();

    assertThat(autoCommit).isFalse();
    verify(connection).commit();
    verify(connection).setAutoCommit(true);
    verify(connection).getAutoCommit();
    verify(connection).close();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void commitExceptionSurfacesUnchangedAndNoRecordingHappens() throws SQLException {
    SQLException cause = new SQLException("commit-fail");
    Mockito.doThrow(cause).when(connection).commit();

    assertThatThrownBy(recordingConnection::commit)
            .isSameAs(cause);

    verify(connection).commit();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void prepareStatementDelegateFailureSurfacesUnchangedAndRecordsBeforeThrowing()
          throws SQLException {
    SQLException cause = new SQLException("execute-fail");
    when(preparedStatement.executeQuery()).thenThrow(cause);

    PreparedStatement wrapped = recordingConnection.prepareStatement(SELECT_SQL);
    assertThatThrownBy(wrapped::executeQuery)
            .isSameAs(cause);

    // Recording happens before delegation, so the call is observed even when the delegate throws.
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, URL, "SELECT",
            SELECT_SQL);
    verify(preparedStatement, times(1)).executeQuery();
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void prepareStatementNullIsForwardedAsNull() throws SQLException {
    when(connection.prepareStatement(SELECT_SQL)).thenReturn(null);

    PreparedStatement wrapped = recordingConnection.prepareStatement(SELECT_SQL);

    assertThat(wrapped).isNull();
    verify(connection).prepareStatement(SELECT_SQL);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void targetIsResolvedOncePerConnection() throws SQLException {
    when(preparedStatement.executeQuery()).thenReturn(mock(ResultSet.class));
    when(statement.executeQuery(SELECT_SQL)).thenReturn(mock(ResultSet.class));

    PreparedStatement ps = recordingConnection.prepareStatement(SELECT_SQL);
    ps.executeQuery();

    Statement st = recordingConnection.createStatement();
    st.executeQuery(SELECT_SQL);

    // The JDBC URL target is resolved once per connection and cached for later factory calls.
    verify(connection, times(1)).getMetaData();
    verify(metaData, times(1)).getURL();
    verify(externalCallRecorder, times(2)).record(ExternalCallRecorder.TYPE_DATABASE, URL,
            "SELECT", SELECT_SQL);
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void decoratorIdentityMethodsReturnConsistentValues() throws SQLException {
    Connection other = new RecordingConnection(connection, externalCallRecorder);

    assertThat(recordingConnection.equals(recordingConnection)).isTrue();
    assertThat(recordingConnection.equals(connection)).isFalse();
    assertThat(recordingConnection.equals(other)).isFalse();
    assertThat(recordingConnection.hashCode())
            .isEqualTo(System.identityHashCode(recordingConnection));
    assertThat(recordingConnection.toString()).contains("RecordingConnection");
  }

  @Test
  void connectionWrapperMethodsForwardToDelegate() throws SQLException {
    when(connection.isWrapperFor(Connection.class)).thenReturn(true);
    when(connection.unwrap(Connection.class)).thenReturn(connection);

    boolean wrapper = recordingConnection.isWrapperFor(Connection.class);
    Connection unwrapped = recordingConnection.unwrap(Connection.class);

    assertThat(wrapper).isTrue();
    assertThat(unwrapped).isSameAs(connection);
    verify(connection).isWrapperFor(Connection.class);
    verify(connection).unwrap(Connection.class);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void connectionIsClosedForwardsWithoutRecording() throws SQLException {
    when(connection.isClosed()).thenReturn(true);

    boolean closed = recordingConnection.isClosed();

    assertThat(closed).isTrue();
    verify(connection).isClosed();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void metadataFailureFallsBackToFallbackTarget() throws SQLException {
    when(connection.getMetaData()).thenThrow(new SQLException("no metadata"));
    when(preparedStatement.executeQuery()).thenReturn(mock(ResultSet.class));

    PreparedStatement wrapped = recordingConnection.prepareStatement(SELECT_SQL);
    wrapped.executeQuery();

    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE,
            RecordingConnection.FALLBACK_TARGET, "SELECT", SELECT_SQL);
    verifyNoMoreInteractions(externalCallRecorder);
  }
}
