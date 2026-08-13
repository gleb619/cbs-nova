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

import javax.sql.DataSource;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Pure-unit tests for the JDBC capture handlers. Wraps a Mockito-mocked {@link DataSource} with
 * {@link DataSourceInvocationHandler} and verifies the statement-factory wrapping contract:
 * returned connections and statements are re-proxied through their typed handlers and recorded
 * calls flow to the {@link ExternalCallRecorder} using the factory SQL.
 */
class ConnectionInvocationHandlerTest {

  private static final String URL = "jdbc:h2:mem:test";
  private static final String SELECT_SQL = "select id from orders where id = ?";
  private static final String CALL_SQL = "{ call my_proc(?) }";

  private ExternalCallRecorder externalCallRecorder;
  private DataSource dataSource;
  private Connection connection;
  private DatabaseMetaData metaData;
  private PreparedStatement preparedStatement;
  private CallableStatement callableStatement;
  private Statement statement;
  private DataSource dataSourceProxy;

  @BeforeEach
  void setUp() throws SQLException {
    externalCallRecorder = mock(ExternalCallRecorder.class);
    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    metaData = mock(DatabaseMetaData.class);
    preparedStatement = mock(PreparedStatement.class);
    callableStatement = mock(CallableStatement.class);
    statement = mock(Statement.class);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(URL);
    when(connection.prepareStatement(SELECT_SQL)).thenReturn(preparedStatement);
    when(connection.prepareCall(CALL_SQL)).thenReturn(callableStatement);
    when(connection.createStatement()).thenReturn(statement);

    DataSourceInvocationHandler handler = new DataSourceInvocationHandler(
            dataSource, externalCallRecorder);
    dataSourceProxy = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{DataSource.class},
            handler);
  }

  private Connection wrappedConnection() throws SQLException {
    return dataSourceProxy.getConnection();
  }

  @Test
  void getConnectionReturnsReproxiedConnection() throws SQLException {
    Connection conn = dataSourceProxy.getConnection();

    assertThat(conn).isNotNull();
    assertThat(Proxy.isProxyClass(conn.getClass())).isTrue();
    verify(dataSource).getConnection();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void prepareStatementReturnsReproxiedStatementWiredToSameRecorder() throws SQLException {
    Connection conn = wrappedConnection();
    PreparedStatement wrapped = conn.prepareStatement(SELECT_SQL);

    assertThat(wrapped).isNotNull();
    assertThat(Proxy.isProxyClass(wrapped.getClass())).isTrue();
    verify(connection).prepareStatement(SELECT_SQL);
    verify(connection).getMetaData();
    verify(metaData).getURL();
    // Statement factory methods themselves do not record — the proxy only forwards.
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void prepareStatementExecuteQueryRecordsFactorySqlAndOperation() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(preparedStatement.executeQuery()).thenReturn(rs);

    Connection conn = wrappedConnection();
    PreparedStatement wrapped = conn.prepareStatement(SELECT_SQL);
    ResultSet actual = wrapped.executeQuery();

    assertThat(actual).isSameAs(rs);
    verify(externalCallRecorder).findMock(ExternalCallRecorder.TYPE_DATABASE, URL, "SELECT");
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, URL, "SELECT",
            SELECT_SQL);
    verify(preparedStatement).executeQuery();
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void prepareCallReturnsReproxiedCallableStatementWiredToSameRecorder() throws SQLException {
    Connection conn = wrappedConnection();
    CallableStatement wrapped = conn.prepareCall(CALL_SQL);

    assertThat(wrapped).isNotNull();
    assertThat(Proxy.isProxyClass(wrapped.getClass())).isTrue();
    verify(connection).prepareCall(CALL_SQL);
    verify(connection).getMetaData();
    verify(metaData).getURL();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void prepareCallExecuteRecordsFactorySqlAndOperation() throws SQLException {
    when(callableStatement.execute()).thenReturn(true);

    Connection conn = wrappedConnection();
    CallableStatement wrapped = conn.prepareCall(CALL_SQL);
    boolean executed = wrapped.execute();

    assertThat(executed).isTrue();
    // First whitespace-separated token of "{ call my_proc(?) }" is "{".
    verify(externalCallRecorder).findMock(ExternalCallRecorder.TYPE_DATABASE, URL, "{");
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, URL, "{", CALL_SQL);
    verify(callableStatement).execute();
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void createStatementReturnsReproxiedStatementWithNoFactorySql() throws SQLException {
    Connection conn = wrappedConnection();
    Statement wrapped = conn.createStatement();

    assertThat(wrapped).isNotNull();
    assertThat(Proxy.isProxyClass(wrapped.getClass())).isTrue();
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

    Connection conn = wrappedConnection();
    Statement wrapped = conn.createStatement();
    ResultSet actual = wrapped.executeQuery(adHoc);

    assertThat(actual).isSameAs(rs);
    verify(externalCallRecorder).findMock(ExternalCallRecorder.TYPE_DATABASE, URL, "DELETE");
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, URL, "DELETE", adHoc);
    verify(statement).executeQuery(adHoc);
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void nonStatementFactoryMethodsForwardAndDoNotTouchRecorder() throws SQLException {
    when(connection.getAutoCommit()).thenReturn(false);

    Connection conn = wrappedConnection();
    conn.commit();
    conn.setAutoCommit(true);
    boolean autoCommit = conn.getAutoCommit();
    conn.close();

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
    org.mockito.Mockito.doThrow(cause).when(connection).commit();

    Connection conn = wrappedConnection();
    assertThatThrownBy(conn::commit)
            .isSameAs(cause);

    verify(connection).commit();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void prepareStatementDelegateFailureSurfacesUnchangedAndRecordsBeforeThrowing()
          throws SQLException {
    SQLException cause = new SQLException("execute-fail");
    when(preparedStatement.executeQuery()).thenThrow(cause);

    Connection conn = wrappedConnection();
    PreparedStatement wrapped = conn.prepareStatement(SELECT_SQL);
    assertThatThrownBy(wrapped::executeQuery)
            .isSameAs(cause);

    // Recording happens before delegation, so the call is observed even when the delegate throws.
    verify(externalCallRecorder).findMock(ExternalCallRecorder.TYPE_DATABASE, URL, "SELECT");
    verify(externalCallRecorder).record(ExternalCallRecorder.TYPE_DATABASE, URL, "SELECT",
            SELECT_SQL);
    verify(preparedStatement, times(1)).executeQuery();
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void getConnectionNullIsForwardedAsNull() throws SQLException {
    when(dataSource.getConnection()).thenReturn(null);

    Connection conn = dataSourceProxy.getConnection();

    assertThat(conn).isNull();
    verify(dataSource).getConnection();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void prepareStatementNullIsForwardedAsNull() throws SQLException {
    when(connection.prepareStatement(SELECT_SQL)).thenReturn(null);

    Connection conn = wrappedConnection();
    PreparedStatement wrapped = conn.prepareStatement(SELECT_SQL);

    assertThat(wrapped).isNull();
    verify(connection).prepareStatement(SELECT_SQL);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void multipleStatementFactoryCallsEachResolveTarget() throws SQLException {
    when(preparedStatement.executeQuery()).thenReturn(mock(ResultSet.class));
    when(statement.executeQuery(SELECT_SQL)).thenReturn(mock(ResultSet.class));

    Connection conn = wrappedConnection();
    PreparedStatement ps = conn.prepareStatement(SELECT_SQL);
    ps.executeQuery();

    Statement st = conn.createStatement();
    st.executeQuery(SELECT_SQL);

    // resolveTarget() is invoked once per statement-factory call.
    verify(connection, times(2)).getMetaData();
    verify(metaData, times(2)).getURL();
    verify(externalCallRecorder, times(2)).findMock(ExternalCallRecorder.TYPE_DATABASE, URL,
            "SELECT");
    verify(externalCallRecorder, times(2)).record(ExternalCallRecorder.TYPE_DATABASE, URL, "SELECT",
            SELECT_SQL);
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void connectionProxyIdentityMethodsReturnConsistentValues() throws SQLException {
    Connection conn = wrappedConnection();
    Connection otherConn = (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{Connection.class},
            new ConnectionInvocationHandler(connection, externalCallRecorder));

    assertThat(conn.equals(conn)).isTrue();
    assertThat(conn.equals(connection)).isFalse();
    assertThat(conn.equals(otherConn)).isFalse();
    assertThat(conn.hashCode()).isEqualTo(System.identityHashCode(conn));
    assertThat(conn.toString()).contains("ConnectionProxy");
  }

  @Test
  void connectionWrapperMethodsForwardToDelegate() throws SQLException {
    when(connection.isWrapperFor(Connection.class)).thenReturn(true);
    when(connection.unwrap(Connection.class)).thenReturn(connection);

    Connection conn = wrappedConnection();
    boolean wrapper = conn.isWrapperFor(Connection.class);
    Connection unwrapped = conn.unwrap(Connection.class);

    assertThat(wrapper).isTrue();
    assertThat(unwrapped).isSameAs(connection);
    verify(connection).isWrapperFor(Connection.class);
    verify(connection).unwrap(Connection.class);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void connectionIsClosedForwardsWithoutRecording() throws SQLException {
    when(connection.isClosed()).thenReturn(true);

    Connection conn = wrappedConnection();
    boolean closed = conn.isClosed();

    assertThat(closed).isTrue();
    verify(connection).isClosed();
    verifyNoInteractions(externalCallRecorder);
  }
}
