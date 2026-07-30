package cbs.nova.starter.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cbs.nova.starter.ExternalCallTracker;
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

class ConnectionInvocationHandlerTest {

  private static final String URL = "jdbc:h2:mem:capture";
  private static final String INSERT_SQL = "insert into orders(name) values (?)";
  private static final String CALL_SQL = "{call do_thing(?)}";

  private ExternalCallTracker tracker;
  private DataSource dataSource;
  private Connection connection;
  private DatabaseMetaData metaData;

  @BeforeEach
  void setUp() throws SQLException {
    tracker = mock(ExternalCallTracker.class);

    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    metaData = mock(DatabaseMetaData.class);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(URL);
  }

  private DataSource wrapDataSource() {
    ConnectionInvocationHandler handler = new ConnectionInvocationHandler(dataSource, tracker);
    return (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{DataSource.class},
            handler);
  }

  @Test
  void getConnectionOnDataSourceProxyReturnsProxiedConnection() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    assertThat(wrapped).isNotNull().isNotSameAs(connection);
    assertThat(Proxy.isProxyClass(wrapped.getClass())).isTrue();
    verify(dataSource).getConnection();
    verifyNoInteractions(tracker);
  }

  @Test
  void getConnectionOverloadOnDataSourceProxyIsIntercepted() throws SQLException {
    when(dataSource.getConnection("user", "pwd")).thenReturn(connection);

    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection("user", "pwd");

    assertThat(wrapped).isNotNull().isNotSameAs(connection);
    assertThat(Proxy.isProxyClass(wrapped.getClass())).isTrue();
    verify(dataSource).getConnection("user", "pwd");
    verifyNoInteractions(tracker);
  }

  @Test
  void prepareStatementReturnsProxiedStatementSharingTracker() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    PreparedStatement raw = mock(PreparedStatement.class);
    when(connection.prepareStatement(INSERT_SQL)).thenReturn(raw);

    PreparedStatement prep = wrapped.prepareStatement(INSERT_SQL);
    assertThat(Proxy.isProxyClass(prep.getClass())).isTrue();

    when(raw.executeQuery()).thenReturn(mock(ResultSet.class));
    prep.executeQuery();

    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, URL, "INSERT", INSERT_SQL);
    verify(raw).executeQuery();
    verifyNoMoreInteractions(tracker);
  }

  @Test
  void prepareCallReturnsProxiedCallableSharingTracker() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    CallableStatement raw = mock(CallableStatement.class);
    when(connection.prepareCall(CALL_SQL)).thenReturn(raw);

    CallableStatement call = wrapped.prepareCall(CALL_SQL);
    assertThat(Proxy.isProxyClass(call.getClass())).isTrue();
    assertThat(call).isInstanceOf(PreparedStatement.class);
    assertThat(call).isInstanceOf(Statement.class);

    when(raw.execute()).thenReturn(true);
    call.execute();

    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, URL, "{CALL", CALL_SQL);
    verify(raw).execute();
    verifyNoMoreInteractions(tracker);
  }

  @Test
  void factorySqlExtractionFindsFirstStringArgumentForPrepareCall() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    CallableStatement raw = mock(CallableStatement.class);
    when(connection.prepareCall(CALL_SQL, ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.CONCUR_READ_ONLY)).thenReturn(raw);

    CallableStatement call = wrapped.prepareCall(CALL_SQL, ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.CONCUR_READ_ONLY);
    assertThat(Proxy.isProxyClass(call.getClass())).isTrue();

    when(raw.execute()).thenReturn(true);
    call.execute();

    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, URL, "{CALL", CALL_SQL);
    verify(raw).execute();
    verifyNoMoreInteractions(tracker);
  }

  @Test
  void createStatementReturnsProxiedStatementWithNullFactorySql() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    Statement raw = mock(Statement.class);
    when(connection.createStatement()).thenReturn(raw);

    Statement stmt = wrapped.createStatement();
    assertThat(Proxy.isProxyClass(stmt.getClass())).isTrue();

    String adHoc = "select 1 from dual";
    when(raw.execute(adHoc)).thenReturn(false);
    stmt.execute(adHoc);

    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, URL, "SELECT", adHoc);
    verify(raw).execute(adHoc);
    verifyNoMoreInteractions(tracker);
  }

  @Test
  void exercisingProxiedPreparedStatementProvesWiring() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    PreparedStatement raw = mock(PreparedStatement.class);
    when(connection.prepareStatement(INSERT_SQL)).thenReturn(raw);
    when(raw.executeUpdate()).thenReturn(3);

    PreparedStatement prep = wrapped.prepareStatement(INSERT_SQL);
    int updated = prep.executeUpdate();

    assertThat(updated).isEqualTo(3);
    verify(raw).executeUpdate();
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, URL, "INSERT", INSERT_SQL);
    verifyNoMoreInteractions(tracker);
  }

  @Test
  void exercisingProxiedStatementWithRawSqlUsesArgumentAsPayload() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    Statement raw = mock(Statement.class);
    when(connection.createStatement()).thenReturn(raw);

    Statement stmt = wrapped.createStatement();
    String adHoc = "update t set x = 1 where id = 5";
    when(raw.executeUpdate(adHoc)).thenReturn(2);
    int updated = stmt.executeUpdate(adHoc);

    assertThat(updated).isEqualTo(2);
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, URL, "UPDATE", adHoc);
    verify(raw).executeUpdate(adHoc);
    verifyNoMoreInteractions(tracker);
  }

  @Test
  void exercisingProxiedStatementWithExecuteBatchUsesBatchOperation() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    Statement raw = mock(Statement.class);
    when(connection.createStatement()).thenReturn(raw);

    Statement stmt = wrapped.createStatement();
    int[] counts = {1, 2, 3};
    when(raw.executeBatch()).thenReturn(counts);
    int[] actual = stmt.executeBatch();

    assertThat(actual).isSameAs(counts);
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, URL, "BATCH", null);
    verify(raw).executeBatch();
    verifyNoMoreInteractions(tracker);
  }

  @Test
  void exerciseEveryRecordedMethodProducesExpectedTrackerCalls() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    PreparedStatement rawPrep = mock(PreparedStatement.class);
    when(connection.prepareStatement(INSERT_SQL)).thenReturn(rawPrep);
    when(rawPrep.executeQuery()).thenReturn(mock(ResultSet.class));
    when(rawPrep.executeUpdate()).thenReturn(0);
    when(rawPrep.execute()).thenReturn(true);
    when(rawPrep.executeLargeUpdate()).thenReturn(0L);
    when(rawPrep.executeBatch()).thenReturn(new int[]{1});
    when(rawPrep.executeLargeBatch()).thenReturn(new long[]{1L});

    PreparedStatement prep = wrapped.prepareStatement(INSERT_SQL);
    prep.executeQuery();
    prep.executeUpdate();
    prep.execute();
    prep.executeBatch();
    prep.executeLargeUpdate();
    prep.executeLargeBatch();

    verify(tracker, times(4))
            .record(eq(ExternalCallTracker.TYPE_DATABASE), eq(URL), eq("INSERT"),
                    eq(INSERT_SQL));
    verify(tracker, times(2))
            .record(eq(ExternalCallTracker.TYPE_DATABASE), eq(URL), eq("BATCH"),
                    eq(INSERT_SQL));
    verifyNoMoreInteractions(tracker);
    verify(rawPrep).executeQuery();
    verify(rawPrep).executeUpdate();
    verify(rawPrep).execute();
    verify(rawPrep).executeBatch();
    verify(rawPrep).executeLargeUpdate();
    verify(rawPrep).executeLargeBatch();
  }

  @Test
  void nonFactoryConnectionMethodsForwardAndDoNotTouchTracker() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    wrapped.commit();
    wrapped.rollback();
    wrapped.close();
    wrapped.setAutoCommit(false);

    verify(connection).commit();
    verify(connection).rollback();
    verify(connection).close();
    verify(connection).setAutoCommit(false);
    verifyNoInteractions(tracker);
  }

  @Test
  void connectionGetterMethodsForwardAndDoNotTouchTracker() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    when(connection.getAutoCommit()).thenReturn(true);
    when(connection.isClosed()).thenReturn(false);

    boolean auto = wrapped.getAutoCommit();
    boolean closed = wrapped.isClosed();

    assertThat(auto).isTrue();
    assertThat(closed).isFalse();
    verify(connection).getAutoCommit();
    verify(connection).isClosed();
    verifyNoInteractions(tracker);
  }

  @Test
  void unwrapAndIsWrapperForForwardToDelegate() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    when(connection.unwrap(Connection.class)).thenReturn(connection);
    when(connection.isWrapperFor(Connection.class)).thenReturn(true);

    Connection unwrapped = wrapped.unwrap(Connection.class);
    boolean wrap = wrapped.isWrapperFor(Connection.class);

    assertThat(unwrapped).isSameAs(connection);
    assertThat(wrap).isTrue();
    verify(connection).unwrap(Connection.class);
    verify(connection).isWrapperFor(Connection.class);
    verifyNoInteractions(tracker);
  }

  @Test
  void factoryMethodOnUnwrappedConnectionIsNotIntercepted() throws SQLException {
    PreparedStatement raw = mock(PreparedStatement.class);
    when(connection.prepareStatement(INSERT_SQL)).thenReturn(raw);

    PreparedStatement result = connection.prepareStatement(INSERT_SQL);

    assertThat(result).isSameAs(raw);
    assertThat(Proxy.isProxyClass(result.getClass())).isFalse();
    verify(connection).prepareStatement(INSERT_SQL);
    verifyNoInteractions(tracker);
  }

  @Test
  void setStringDoesNotRecordButExecuteUpdateDoes() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    PreparedStatement raw = mock(PreparedStatement.class);
    when(connection.prepareStatement(INSERT_SQL)).thenReturn(raw);
    when(raw.executeUpdate()).thenReturn(1);

    PreparedStatement prep = wrapped.prepareStatement(INSERT_SQL);
    prep.setString(1, "name");
    prep.executeUpdate();

    verify(raw).setString(1, "name");
    verify(raw).executeUpdate();
    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE, URL, "INSERT", INSERT_SQL);
    verifyNoMoreInteractions(tracker);
  }

  @Test
  void getterMethodsOnProxiedPreparedStatementDoNotTouchTracker() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    PreparedStatement raw = mock(PreparedStatement.class);
    when(connection.prepareStatement(INSERT_SQL)).thenReturn(raw);

    PreparedStatement prep = wrapped.prepareStatement(INSERT_SQL);
    prep.getResultSet();
    prep.getUpdateCount();
    prep.close();

    verify(raw).getResultSet();
    verify(raw).getUpdateCount();
    verify(raw).close();
    verifyNoInteractions(tracker);
  }

  @Test
  void factoryDelegateFailureSurfacesOriginalException() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    SQLException cause = new SQLException("prepare-fail");
    when(connection.prepareStatement(INSERT_SQL)).thenThrow(cause);

    assertThatThrownBy(() -> wrapped.prepareStatement(INSERT_SQL))
            .isSameAs(cause);
    verifyNoInteractions(tracker);
  }

  @Test
  void factoryDelegateFailureSurfacesOriginalExceptionForCallable() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    SQLException cause = new SQLException("call-fail");
    when(connection.prepareCall(CALL_SQL)).thenThrow(cause);

    assertThatThrownBy(() -> wrapped.prepareCall(CALL_SQL))
            .isSameAs(cause);
    verifyNoInteractions(tracker);
  }

  @Test
  void factoryDelegateFailureSurfacesOriginalExceptionForCreateStatement()
          throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    SQLException cause = new SQLException("create-fail");
    when(connection.createStatement()).thenThrow(cause);

    assertThatThrownBy(wrapped::createStatement)
            .isSameAs(cause);
    verifyNoInteractions(tracker);
  }

  @Test
  void dataSourceGetConnectionFailureSurfacesOriginalException() throws SQLException {
    SQLException cause = new SQLException("connect-fail");
    when(dataSource.getConnection()).thenThrow(cause);

    DataSource proxy = wrapDataSource();

    assertThatThrownBy(proxy::getConnection)
            .isSameAs(cause);
    verifyNoInteractions(tracker);
  }

  @Test
  void nonFactoryConnectionMethodFailureSurfacesOriginalException() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    SQLException cause = new SQLException("commit-fail");
    doThrow(cause).when(connection).commit();

    assertThatThrownBy(wrapped::commit)
            .isSameAs(cause);
    verifyNoInteractions(tracker);
  }

  @Test
  void dataSourceGetConnectionReturnsNullWhenDelegateReturnsNull() throws SQLException {
    DataSource proxy = wrapDataSource();
    when(dataSource.getConnection()).thenReturn(null);

    Connection result = proxy.getConnection();

    assertThat(result).isNull();
    verifyNoInteractions(tracker);
  }

  @Test
  void factoryReturnsNullWhenStatementDelegateReturnsNull() throws SQLException {
    DataSource proxy = wrapDataSource();
    Connection wrapped = proxy.getConnection();

    when(connection.createStatement()).thenReturn(null);
    Statement result = wrapped.createStatement();

    assertThat(result).isNull();
    verifyNoInteractions(tracker);
  }

  @Test
  void connectionMetadataFailureFallsBackToDefaultTarget() throws SQLException {
    DataSource proxy = wrapDataSource();
    when(metaData.getURL()).thenThrow(new SQLException("no-meta"));

    Connection wrapped = proxy.getConnection();
    PreparedStatement raw = mock(PreparedStatement.class);
    when(connection.prepareStatement(INSERT_SQL)).thenReturn(raw);

    PreparedStatement prep = wrapped.prepareStatement(INSERT_SQL);
    when(raw.executeUpdate()).thenReturn(1);
    prep.executeUpdate();

    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE,
            ConnectionInvocationHandler.FALLBACK_TARGET, "INSERT", INSERT_SQL);
    verifyNoMoreInteractions(tracker);
  }

  @Test
  void blankMetadataUrlFallsBackToDefaultTarget() throws SQLException {
    DataSource proxy = wrapDataSource();
    when(metaData.getURL()).thenReturn("   ");
    Connection wrapped = proxy.getConnection();

    PreparedStatement raw = mock(PreparedStatement.class);
    when(connection.prepareStatement(INSERT_SQL)).thenReturn(raw);

    PreparedStatement prep = wrapped.prepareStatement(INSERT_SQL);
    when(raw.executeUpdate()).thenReturn(1);
    prep.executeUpdate();

    verify(tracker).record(ExternalCallTracker.TYPE_DATABASE,
            ConnectionInvocationHandler.FALLBACK_TARGET, "INSERT", INSERT_SQL);
    verifyNoMoreInteractions(tracker);
  }
}
