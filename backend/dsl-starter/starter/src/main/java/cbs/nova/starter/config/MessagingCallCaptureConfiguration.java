package cbs.nova.starter.config;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.preview.MessagingCallCaptureProducerFactoryBeanPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnClass(name = "org.springframework.kafka.core.ProducerFactory")
public class MessagingCallCaptureConfiguration {

  @Bean
  public static BeanPostProcessor messagingCallCaptureProducerFactoryPostProcessor(
          ExternalCallRecorder externalCallRecorder) {
    return new MessagingCallCaptureProducerFactoryBeanPostProcessor(externalCallRecorder);
  }
}
