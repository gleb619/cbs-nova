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

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Pure-unit tests for {@link RecordingDataSource}. Verifies typed delegation for {@link DataSource}
 * / {@link CommonDataSource} / {@link java.sql.Wrapper} methods, decorator identity semantics, and
 * the connection-wrapping contract.
 */
class RecordingDataSourceTest {

  private ExternalCallRecorder externalCallRecorder;
  private DataSource dataSource;
  private Connection connection;
  private RecordingDataSource recordingDataSource;

  @BeforeEach
  void setUp() throws SQLException {
    externalCallRecorder = mock(ExternalCallRecorder.class);
    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    when(dataSource.getConnection()).thenReturn(connection);

    recordingDataSource = new RecordingDataSource(dataSource, externalCallRecorder);
  }

  @Test
  void getConnectionWrapsReturnedConnection() throws SQLException {
    Connection wrapped = recordingDataSource.getConnection();

    assertThat(wrapped).isInstanceOf(RecordingConnection.class);
    verify(dataSource).getConnection();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getConnectionWithCredentialsWrapsReturnedConnection() throws SQLException {
    when(dataSource.getConnection("user", "pass")).thenReturn(connection);

    Connection wrapped = recordingDataSource.getConnection("user", "pass");

    assertThat(wrapped).isInstanceOf(RecordingConnection.class);
    verify(dataSource).getConnection("user", "pass");
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void getConnectionNullIsForwardedAsNull() throws SQLException {
    when(dataSource.getConnection()).thenReturn(null);

    Connection wrapped = recordingDataSource.getConnection();

    assertThat(wrapped).isNull();
    verify(dataSource).getConnection();
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void dataSourceWrapperMethodsForwardToDelegate() throws SQLException {
    when(dataSource.isWrapperFor(DataSource.class)).thenReturn(true);
    when(dataSource.unwrap(DataSource.class)).thenReturn(dataSource);

    boolean wrapper = recordingDataSource.isWrapperFor(DataSource.class);
    DataSource unwrapped = recordingDataSource.unwrap(DataSource.class);

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

    int timeout = recordingDataSource.getLoginTimeout();
    recordingDataSource.setLoginTimeout(60);

    assertThat(timeout).isEqualTo(30);
    verify(dataSource).getLoginTimeout();
    verify(dataSource).setLoginTimeout(60);
    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void decoratorIdentityMethodsReturnConsistentValues() {
    RecordingDataSource otherDecorator = new RecordingDataSource(dataSource,
            externalCallRecorder);

    assertThat(recordingDataSource.equals(recordingDataSource)).isTrue();
    assertThat(recordingDataSource.equals(dataSource)).isFalse();
    assertThat(recordingDataSource.equals(otherDecorator)).isFalse();
    assertThat(recordingDataSource.hashCode())
            .isEqualTo(System.identityHashCode(recordingDataSource));
    assertThat(recordingDataSource.toString()).contains("RecordingDataSource");
  }
}
