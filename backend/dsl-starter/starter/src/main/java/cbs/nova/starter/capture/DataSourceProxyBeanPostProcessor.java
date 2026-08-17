package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;

public class DataSourceProxyBeanPostProcessor implements BeanPostProcessor {

  private final ExternalCallRecorder externalCallRecorder;

  public DataSourceProxyBeanPostProcessor(@NonNull ExternalCallRecorder externalCallRecorder) {
    this.externalCallRecorder = externalCallRecorder;
  }

  @Override
  public Object postProcessAfterInitialization(@NonNull Object bean, @Nullable String beanName) {
    if (!(bean instanceof DataSource dataSource)) {
      return bean;
    }
    return new RecordingDataSource(dataSource, externalCallRecorder);
  }
}
