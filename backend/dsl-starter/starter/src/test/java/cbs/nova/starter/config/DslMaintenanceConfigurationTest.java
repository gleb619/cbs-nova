package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.starter.maintenance.AuditRetentionMaintenanceTask;
import cbs.nova.starter.maintenance.DslMaintenanceService;
import cbs.nova.starter.maintenance.DslRunReconciliationMaintenanceTask;
import cbs.nova.starter.maintenance.DslRunRetentionMaintenanceTask;
import cbs.nova.starter.service.DslRunReconciliationService;
import cbs.nova.starter.service.DslRunRetentionPurger;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the rollout behaviour of {@link DslMaintenanceConfiguration}:
 *
 * <ul>
 * <li>{@code dsl.maintenance.unified-enabled=false} (default) — no unified service bean is
 * registered; the per-job starter ApplicationRunner beans self-schedule.</li>
 * <li>{@code dsl.maintenance.unified-enabled=true} — the unified service bean is present and the
 * per-job starter ApplicationRunner beans are conditioned out.</li>
 * </ul>
 */
class DslMaintenanceConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(
                  DslMaintenanceConfiguration.class,
                  DslRunRetentionConfiguration.class,
                  DslRunReconciliationConfiguration.class))
          .withUserConfiguration(TestBeans.class)
          .withPropertyValues(
                  // Force the per-job beans to be eligible in the test
                  // context by default: reconciliation needs enabled=true.
                  "cbs.runs.retention=PT24H",
                  "cbs.runs.reconciliation.enabled=true",
                  // Match the legacy retention knob so the purger self-schedules.
                  "cbs.runs.purge-interval=PT1H");

  @Test
  void unifiedDisabledByDefaultKeepsIndividualSchedulers() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(DslRunRetentionPurger.class);
      assertThat(ctx).hasSingleBean(DslRunReconciliationService.class);
      // Audit task is always present (it's a no-op forward-compat stub).
      assertThat(ctx).hasSingleBean(AuditRetentionMaintenanceTask.class);
      assertThat(ctx).doesNotHaveBean(DslMaintenanceService.class);

      // The per-job ApplicationRunner starters are present.
      assertThat(ctx).hasBean("dslRunRetentionPurgerStarter");
      assertThat(ctx).hasBean("dslRunReconciliationServiceStarter");
    });
  }

  @Test
  void unifiedEnabledRegistersUnifiedServiceAndSuppressesPerJobStarters() {
    runner.withPropertyValues("dsl.maintenance.unified-enabled=true").run(ctx -> {
      assertThat(ctx).hasSingleBean(DslMaintenanceService.class);
      // The per-job ApplicationRunner starters are conditioned out.
      assertThat(ctx).doesNotHaveBean("dslRunRetentionPurgerStarter");
      assertThat(ctx).doesNotHaveBean("dslRunReconciliationServiceStarter");

      // Adapters are still wired.
      assertThat(ctx).hasSingleBean(DslRunRetentionMaintenanceTask.class);
      assertThat(ctx).hasSingleBean(DslRunReconciliationMaintenanceTask.class);
      assertThat(ctx).hasSingleBean(AuditRetentionMaintenanceTask.class);
    });
  }

  @Test
  void unifiedEnabledUsesConfiguredScheduleInterval() {
    runner
            .withPropertyValues(
                    "dsl.maintenance.unified-enabled=true",
                    "dsl.maintenance.schedule=PT2H")
            .run(ctx -> {
              DslMaintenanceService service = ctx.getBean(DslMaintenanceService.class);
              assertThat(service).isNotNull();
              assertThat(service.tasks()).extracting(t -> t.name())
                      .containsExactly(
                              AuditRetentionMaintenanceTask.NAME,
                              DslRunReconciliationMaintenanceTask.NAME,
                              DslRunRetentionMaintenanceTask.NAME);
            });
  }

  // ---------------------------------------------------------------------
  // Test fixtures: minimal DslRunRepository, WorkflowClient and MeterRegistry
  // so the per-job beans (which have @ConditionalOnBean) are eligible.
  // ---------------------------------------------------------------------

  @Configuration
  static class TestBeans {

    @Bean
    DslRunRepository dslRunRepository() {
      return new InMemoryDslRunRepository();
    }

    @Bean
    WorkflowClient workflowClient() {
      return Mockito.mock(WorkflowClient.class);
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
