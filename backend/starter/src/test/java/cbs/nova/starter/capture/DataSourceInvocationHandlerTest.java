package cbs.nova.starter.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Pure-unit tests for {@link DataSourceInvocationHandler}. Verifies typed dispatch for
 * {@link DataSource} / {@link CommonDataSource} / {@link java.sql.Wrapper} methods, proxy identity
 * semantics, and the connection-wrapping contract.
 */
class DataSourceInvocationHandlerTest {

  private ExternalCallRecorder externalCallRecorder;
  private DataSource dataSource;
  private Connection connection;
  private DataSource proxy;

  @BeforeEach
  void setUp() throws SQLException {
    externalCallRecorder = mock(ExternalCallRecorder.class);
    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    when(dataSource.getConnection()).thenReturn(connection);

    proxy = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{DataSource.class},
            new DataSourceInvocationHandler(dataSource, externalCallRecorder));
  }

  @Test
  void getConnectionWrapsReturnedConnection() throws SQLException {
    Connection wrapped = proxy.getConnection();

    assertThat(wrapped).isNotNull();
    assertThat(Proxy.isProxyClass(wrapped.getClass())).isTrue();
    verify(dataSource).getConnection();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getConnectionWithCredentialsWrapsReturnedConnection() throws SQLException {
    when(dataSource.getConnection("user", "pass")).thenReturn(connection);

    Connection wrapped = proxy.getConnection("user", "pass");

    assertThat(wrapped).isNotNull();
    assertThat(Proxy.isProxyClass(wrapped.getClass())).isTrue();
    verify(dataSource).getConnection("user", "pass");
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getConnectionNullIsForwardedAsNull() throws SQLException {
    when(dataSource.getConnection()).thenReturn(null);

    Connection wrapped = proxy.getConnection();

    assertThat(wrapped).isNull();
    verify(dataSource).getConnection();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void dataSourceWrapperMethodsForwardToDelegate() throws SQLException {
    when(dataSource.isWrapperFor(DataSource.class)).thenReturn(true);
    when(dataSource.unwrap(DataSource.class)).thenReturn(dataSource);

    boolean wrapper = proxy.isWrapperFor(DataSource.class);
    DataSource unwrapped = proxy.unwrap(DataSource.class);

    assertThat(wrapper).isTrue();
    assertThat(unwrapped).isSameAs(dataSource);
    verify(dataSource).isWrapperFor(DataSource.class);
    verify(dataSource).unwrap(DataSource.class);
    verifyNoMoreInteractions(dataSource);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void commonDataSourceMethodsForwardToDelegate() throws SQLException {
    when(dataSource.getLoginTimeout()).thenReturn(30);

    int timeout = proxy.getLoginTimeout();
    proxy.setLoginTimeout(60);

    assertThat(timeout).isEqualTo(30);
    verify(dataSource).getLoginTimeout();
    verify(dataSource).setLoginTimeout(60);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void proxyIdentityMethodsReturnConsistentValues() {
    DataSource otherProxy = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{DataSource.class},
            new DataSourceInvocationHandler(dataSource, externalCallRecorder));

    assertThat(proxy.equals(proxy)).isTrue();
    assertThat(proxy.equals(dataSource)).isFalse();
    assertThat(proxy.equals(otherProxy)).isFalse();
    assertThat(proxy.hashCode()).isEqualTo(System.identityHashCode(proxy));
    assertThat(proxy.toString()).contains("DataSourceProxy");
  }
}
