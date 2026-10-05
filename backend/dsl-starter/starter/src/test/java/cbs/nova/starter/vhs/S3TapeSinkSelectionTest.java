package cbs.nova.starter.vhs;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.VhsConfiguration;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.mock;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.ObjectMapper;

class S3TapeSinkSelectionTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(S3VhsTestConfig.class);

  @Test
  void localSinkByDefault() {
    runner
            .withPropertyValues(
                    "cbs.vhs.enabled=true",
                    "cbs.vhs.recordableRoutes=Ping")
            .run(ctx -> {
              assertThat(ctx).hasSingleBean(VhsTapeSink.class);
              assertThat(ctx).hasBean("vhsTapeSink");
              assertThat(ctx.getBean(VhsTapeSink.class)).isInstanceOf(LocalFileTapeSink.class);
              assertThat(ctx)
                      .hasSingleBean(cbs.nova.starter.vhs.management.LocalFileTapeStore.class);
            });
  }

  @Test
  void s3SinkWhenTypeIsS3() {
    runner
            .withPropertyValues(
                    "cbs.vhs.enabled=true",
                    "cbs.vhs.recordableRoutes=Ping",
                    "cbs.vhs.sink.type=s3",
                    "cbs.vhs.sink.s3.bucket=vhs-bucket",
                    "cbs.vhs.sink.s3.prefix=tapes/")
            .run(ctx -> {
              assertThat(ctx).hasSingleBean(VhsTapeSink.class);
              assertThat(ctx.getBean(VhsTapeSink.class)).isInstanceOf(S3TapeSink.class);
              assertThat(ctx).doesNotHaveBean(cbs.nova.starter.vhs.LocalFileTapeSink.class);
              assertThat(ctx)
                      .doesNotHaveBean(cbs.nova.starter.vhs.management.LocalFileTapeStore.class);
            });
  }

  @Configuration
  @Import(VhsConfiguration.class)
  static class S3VhsTestConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    DslExecutionEventBus eventBus() {
      return new DslExecutionEventBus();
    }

    @Bean
    S3Client s3Client() {
      return mock(S3Client.class);
    }
  }
}
