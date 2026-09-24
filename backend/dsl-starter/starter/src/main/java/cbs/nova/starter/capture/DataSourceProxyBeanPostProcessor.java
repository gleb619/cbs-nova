package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;

@RequiredArgsConstructor
public class DataSourceProxyBeanPostProcessor implements BeanPostProcessor {

  private final @NonNull ExternalCallRecorder externalCallRecorder;

  @Override
  public Object postProcessAfterInitialization(@NonNull Object bean, @Nullable String beanName) {
    if (!(bean instanceof DataSource dataSource)) {
      return bean;
    }
    return new RecordingDataSource(dataSource, externalCallRecorder);
  }
}
