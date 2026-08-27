package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.DslErrorHandlingConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.converter.DslExceptionMapper;
import cbs.nova.starter.model.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

class DslExceptionMapperConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(DslErrorHandlingConfiguration.class));

  @Test
  void registersDefaultMapperWhenNoCustomBeanExists() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(DslExceptionMapper.class);
      assertThat(ctx).hasSingleBean(DefaultDslExceptionMapper.class);
      assertThat(ctx.getBean(DslExceptionMapper.class))
              .isInstanceOf(DefaultDslExceptionMapper.class);
    });
  }

  @Test
  void customMapperBeanWinsOverDefault() {
    runner.withUserConfiguration(CustomMapperConfiguration.class).run(ctx -> {
      assertThat(ctx).hasSingleBean(DslExceptionMapper.class);
      assertThat(ctx).doesNotHaveBean(DefaultDslExceptionMapper.class);
      assertThat(ctx.getBean(DslExceptionMapper.class))
              .isSameAs(ctx.getBean("customDslExceptionMapper"));
    });
  }

  @Configuration
  static class CustomMapperConfiguration {

    @Bean
    DslExceptionMapper customDslExceptionMapper() {
      return new DslExceptionMapper() {
        @Override
        public ResponseEntity<ErrorResponse> handle(Exception exception, WebRequest request) {
          return ResponseEntity.ok()
                  .body(new ErrorResponse("CUSTOM", exception.getMessage(), null, null, null));
        }
      };
    }
  }
}
