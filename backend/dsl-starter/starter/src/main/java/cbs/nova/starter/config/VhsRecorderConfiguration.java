package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsVhsProperties;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.vhs.LocalFileTapeSink;
import cbs.nova.starter.vhs.VhsRecorder;
import cbs.nova.starter.vhs.VhsTapeSink;
import cbs.nova.starter.vhs.scrub.VhsScrubber;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conditional configuration for the VHS execution recorder.
 *
 * <p>
 * All recorder beans are registered only when {@code cbs.vhs.enabled=true}. When disabled (the
 * default) the beans are absent, leaving the hot execution path unchanged and making the
 * zero-overhead guarantee testable.
 */
@Configuration
@ConditionalOnProperty(name = "cbs.vhs.enabled", havingValue = "true")
@EnableConfigurationProperties(CbsVhsProperties.class)
public class VhsRecorderConfiguration {

  @Bean
  @ConditionalOnProperty(name = "cbs.vhs.sink.type", havingValue = "local", matchIfMissing = true)
  VhsTapeSink vhsTapeSink(CbsVhsProperties properties) {
    return new LocalFileTapeSink(properties);
  }

  @Bean
  VhsScrubber vhsScrubber(CbsVhsProperties properties) {
    CbsVhsProperties.Scrub scrub = properties.scrub();
    return new VhsScrubber(scrub.enabled(), scrub.rules(), scrub.maskValue(), scrub.seed());
  }

  @Bean
  VhsRecorder vhsRecorder(
          VhsTapeSink sink, VhsScrubber scrubber, CbsVhsProperties properties,
          DslExecutionEventBus eventBus) {
    VhsRecorder recorder = new VhsRecorder(sink, properties.recordableRoutes(), scrubber);
    eventBus.register(recorder);
    return recorder;
  }
}
