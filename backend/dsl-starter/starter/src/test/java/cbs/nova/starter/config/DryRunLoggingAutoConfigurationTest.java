package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the wiring of {@link DryRunLoggingAutoConfiguration} without booting a full Spring
 * Boot application.
 *
 * <p>Conditional matrix pinned by these tests:
 * <ul>
 *   <li>{@code dryRunLoggingContext} bean: gated by {@code @ConditionalOnMissingBean} AND
 *       {@code @ConditionalOnProperty(name="cbs.nova.dryRun.context.type", havingValue="threadlocal",
 *       matchIfMissing=true)}.</li>
 *   <li>{@code dryRunLogBufferRegistry} bean: only gated by {@code @ConditionalOnMissingBean}.</li>
 *   <li>{@code dryRunLogbackAppender} bean: only gated by {@code @ConditionalOnMissingBean}; it
 *       depends on the (auto or user-supplied) {@link DryRunLoggingContext} and
 *       {@link DryRunLogBufferRegistry}.</li>
 *   <li>{@code dryRunLogbackAppenderInstaller}: unconditional {@link ApplicationRunner}.</li>
 * </ul>
 */
class DryRunLoggingAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(DryRunLoggingAutoConfiguration.class));

  @Test
  void allBeansAreWiredByDefault() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(DryRunLoggingContext.class);
      assertThat(ctx.getBean(DryRunLoggingContext.class))
              .isInstanceOf(ThreadLocalDryRunLoggingContext.class);
      assertThat(ctx).hasSingleBean(DryRunLogBufferRegistry.class);
      assertThat(ctx).hasSingleBean(DryRunLogbackAppender.class);
      assertThat(ctx).hasSingleBean(ApplicationRunner.class);

      // Wiring integrity: the appender must be the one that was injected with the auto-configured
      // context and registry.
      DryRunLogbackAppender appender = ctx.getBean(DryRunLogbackAppender.class);
      assertThat(appender.getDryRunLoggingContext())
              .isSameAs(ctx.getBean(DryRunLoggingContext.class));
      assertThat(appender.getBufferRegistry())
              .isSameAs(ctx.getBean(DryRunLogBufferRegistry.class));
    });
  }

  @Test
  void explicitThreadLocalContextTypeEnablesContextBean() {
    runner
            .withPropertyValues("cbs.nova.dryRun.context.type=threadlocal")
            .run(ctx -> {
              assertThat(ctx).hasSingleBean(DryRunLoggingContext.class);
              assertThat(ctx).hasSingleBean(DryRunLogBufferRegistry.class);
              assertThat(ctx).hasSingleBean(DryRunLogbackAppender.class);
            });
  }

  @Test
  void nonThreadLocalContextTypeFailsContextStartBecauseAppenderDependsOnContext() {
    // The dryRunLoggingContext bean is gated by cbs.nova.dryRun.context.type=threadlocal. With a
    // non-matching value the bean is skipped, and the dryRunLogbackAppender bean (which depends on
    // DryRunLoggingContext) cannot be created, so the context fails to start.
    runner
            .withPropertyValues("cbs.nova.dryRun.context.type=scoped")
            .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void userSuppliedLoggingContextWinsAndAppenderStillWired() {
    runner.withUserConfiguration(CustomLoggingContextConfiguration.class).run(ctx -> {
      assertThat(ctx).hasSingleBean(DryRunLoggingContext.class);
      assertThat(ctx.getBean(DryRunLoggingContext.class))
              .isSameAs(ctx.getBean("customDryRunLoggingContext"));

      // The user supplied only the context, not an appender, so the starter's appender bean is
      // still registered (its @ConditionalOnMissingBean targets DryRunLogbackAppender, not the
      // context) and is now wired against the user-supplied context.
      assertThat(ctx).hasSingleBean(DryRunLogBufferRegistry.class);
      assertThat(ctx).hasSingleBean(DryRunLogbackAppender.class);
      DryRunLogbackAppender appender = ctx.getBean(DryRunLogbackAppender.class);
      assertThat(appender.getDryRunLoggingContext())
              .isSameAs(ctx.getBean("customDryRunLoggingContext"));
    });
  }

  @Test
  void userSuppliedBufferRegistryWins() {
    runner.withUserConfiguration(CustomBufferRegistryConfiguration.class).run(ctx -> {
      assertThat(ctx).hasSingleBean(DryRunLogBufferRegistry.class);
      assertThat(ctx.getBean(DryRunLogBufferRegistry.class))
              .isSameAs(ctx.getBean("customDryRunLogBufferRegistry"));
      // The appender is still present and bound to the user-supplied registry.
      DryRunLogbackAppender appender = ctx.getBean(DryRunLogbackAppender.class);
      assertThat(appender.getBufferRegistry())
              .isSameAs(ctx.getBean("customDryRunLogBufferRegistry"));
    });
  }

  @Test
  void userSuppliedAppenderWinsAndStarterAppenderBacksOff() {
    runner.withUserConfiguration(CustomAppenderConfiguration.class).run(ctx -> {
      assertThat(ctx).hasSingleBean(DryRunLogbackAppender.class);
      assertThat(ctx.getBean(DryRunLogbackAppender.class))
              .isSameAs(ctx.getBean("customDryRunLogbackAppender"));
      // The installer ApplicationRunner still exists — it has no @ConditionalOnMissingBean — but
      // it is now wired against the user-supplied appender.
      assertThat(ctx).hasSingleBean(ApplicationRunner.class);
    });
  }

  @Configuration
  static class CustomLoggingContextConfiguration {

    @Bean
    DryRunLoggingContext customDryRunLoggingContext() {
      return new ThreadLocalDryRunLoggingContext();
    }
  }

  @Configuration
  static class CustomBufferRegistryConfiguration {

    @Bean
    DryRunLogBufferRegistry customDryRunLogBufferRegistry() {
      return new DryRunLogBufferRegistry();
    }
  }

  @Configuration
  static class CustomAppenderConfiguration {

    @Bean
    DryRunLogbackAppender customDryRunLogbackAppender(
            DryRunLoggingContext dryRunLoggingContext,
            DryRunLogBufferRegistry bufferRegistry) {
      var appender = new DryRunLogbackAppender(dryRunLoggingContext, bufferRegistry);
      appender.setName("CUSTOM_DRY_RUN");
      return appender;
    }
  }
}
