package cbs.nova.starter.config;

import cbs.nova.starter.capture.ExternalCallFeignInterceptor;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration that registers a Feign {@link feign.RequestInterceptor} bean which records
 * outgoing HTTP requests as external "http" calls via {@link ExternalCallRecorder}.
 *
 * <p>
 * Only activates when OpenFeign is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(name = "feign.RequestInterceptor")
public class FeignCallAutoConfiguration {

  @Bean(name = "externalCallFeignInterceptor")
  public ExternalCallFeignInterceptor externalCallFeignInterceptor(
          ExternalCallRecorder externalCallRecorder) {
    return new ExternalCallFeignInterceptor(externalCallRecorder);
  }
}
