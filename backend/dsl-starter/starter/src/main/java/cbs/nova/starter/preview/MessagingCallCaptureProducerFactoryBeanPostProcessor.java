package cbs.nova.starter.preview;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.core.ProducerFactory;

@RequiredArgsConstructor
public class MessagingCallCaptureProducerFactoryBeanPostProcessor implements BeanPostProcessor {

  private final ExternalCallRecorder externalCallRecorder;

  @Override
  public Object postProcessAfterInitialization(@NonNull Object bean, @Nullable String beanName) {
    if (bean instanceof ProducerFactory) {
      return new MessagingCallCaptureProducerFactory<>((ProducerFactory<?, ?>) bean,
              externalCallRecorder);
    }
    return bean;
  }
}
