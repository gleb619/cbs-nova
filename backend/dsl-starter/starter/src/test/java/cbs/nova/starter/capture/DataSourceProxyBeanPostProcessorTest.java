package cbs.nova.starter.capture;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Pure-unit tests for {@link DataSourceProxyBeanPostProcessor}. Verifies the bean-wrapping
 * decision: only {@link DataSource} beans are wrapped; all other beans are returned unchanged.
 */
class DataSourceProxyBeanPostProcessorTest {

  private ExternalCallRecorder externalCallRecorder;
  private DataSourceProxyBeanPostProcessor postProcessor;

  @BeforeEach
  void setUp() {
    externalCallRecorder = Mockito.mock(ExternalCallRecorder.class);
    postProcessor = new DataSourceProxyBeanPostProcessor(externalCallRecorder);
  }

  @Test
  void dataSourceBeanIsWrapped() {
    DataSource dataSource = Mockito.mock(DataSource.class);

    Object processed = postProcessor.postProcessAfterInitialization(dataSource, "ds");

    assertThat(processed).isInstanceOf(RecordingDataSource.class);
    assertThat(processed).isNotSameAs(dataSource);
  }

  @Test
  void nonDataSourceBeanIsReturnedUnchanged() {
    Object plain = new Object();

    Object processed = postProcessor.postProcessAfterInitialization(plain, "not-a-ds");

    assertThat(processed).isSameAs(plain);
  }

  @Test
  void stringBeanIsReturnedUnchanged() {
    Object processed = postProcessor.postProcessAfterInitialization("hello", "stringBean");

    assertThat(processed).isSameAs("hello");
  }

  @Test
  void nullBeanIsReturnedUnchanged() {
    Object processed = postProcessor.postProcessAfterInitialization(null, "nullBean");

    assertThat(processed).isNull();
  }

  @Test
  void postProcessBeforeInitializationReturnsBeanUnchanged() {
    DataSource dataSource = Mockito.mock(DataSource.class);

    Object processed = postProcessor.postProcessBeforeInitialization(dataSource, "ds");

    assertThat(processed).isSameAs(dataSource);
  }
}
