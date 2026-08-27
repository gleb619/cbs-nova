package cbs.nova.starter.config;

import cbs.nova.starter.capture.ExternalCallFeignInterceptor;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnClass(name = "feign.RequestInterceptor")
public class FeignCallConfiguration {

  @Bean(name = "externalCallFeignInterceptor")
  public ExternalCallFeignInterceptor externalCallFeignInterceptor(
          ExternalCallRecorder externalCallRecorder) {
    return new ExternalCallFeignInterceptor(externalCallRecorder);
  }
}
