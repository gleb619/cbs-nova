package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsVhsProperties;
import cbs.nova.starter.config.properties.CbsVhsReplayProperties;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.vhs.LocalFileTapeSink;
import cbs.nova.starter.vhs.VhsRecorder;
import cbs.nova.starter.vhs.VhsTapeSink;
import cbs.nova.starter.vhs.replay.VhsCallDriver;
import cbs.nova.starter.vhs.replay.VhsCallDrivers;
import cbs.nova.starter.vhs.replay.VhsReplayEngine;
import cbs.nova.starter.vhs.scrub.VhsScrubber;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conditional configuration for the VHS tape subsystem: the execution recorder and the replay
 * engine.
 *
 * <p>
 * Recorder beans are registered only when {@code cbs.vhs.enabled=true}. Replay beans are registered
 * only when {@code cbs.vhs.replay.enabled=true}; when disabled (the default) both sets of beans are
 * absent, leaving the hot execution path unchanged and making the zero-overhead guarantee testable.
 *
 * <p>
 * <b>Operational safety:</b> the replay target is resolved through {@link VhsCallDrivers#resolve},
 * which defaults to {@code dry-run} (no side effects) and requires a two-key opt-in
 * ({@code cbs.vhs.replay.allow-production=true} AND {@code CBS_VHS_REPLAY_ALLOW_PRODUCTION=1}) for
 * any production-like target. Do not bypass it.
 */
@Configuration
@ConditionalOnProperty(name = "cbs.vhs.enabled", havingValue = "true")
@EnableConfigurationProperties({CbsVhsProperties.class, CbsVhsReplayProperties.class})
public class VhsConfiguration {

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

  @Bean
  @ConditionalOnProperty(name = "cbs.vhs.replay.enabled", havingValue = "true")
  VhsCallDriver vhsReplayCallDriver(CbsVhsReplayProperties replayProperties) {
    return VhsCallDrivers.resolve(replayProperties);
  }

  @Bean
  @ConditionalOnProperty(name = "cbs.vhs.replay.enabled", havingValue = "true")
  VhsReplayEngine vhsReplayEngine(
          CbsVhsReplayProperties replayProperties, VhsCallDriver vhsReplayCallDriver) {
    return new VhsReplayEngine(replayProperties, vhsReplayCallDriver);
  }
}
