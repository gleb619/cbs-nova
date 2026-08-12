package cbs.nova.starter.preview;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration that registers a {@link BeanPostProcessor} wrapping every
 * {@code org.springframework.kafka.core.ProducerFactory} bean so Kafka message publishing is
 * recorded as external "messaging" calls during preview/explain runs.
 *
 * <p>
 * Activates only when Spring for Apache Kafka is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.kafka.core.ProducerFactory")
public class MessagingCallCaptureAutoConfiguration {

  @Bean
  static BeanPostProcessor messagingCallCaptureProducerFactoryPostProcessor(
          ExternalCallRecorder externalCallRecorder) {
    return new MessagingCallCaptureProducerFactoryBeanPostProcessor(externalCallRecorder);
  }
}
