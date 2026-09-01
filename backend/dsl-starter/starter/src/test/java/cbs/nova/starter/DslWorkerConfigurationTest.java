package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cbs.nova.starter.config.DslWorkerConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicReference;

class DslWorkerConfigurationTest {

  @Test
  void workerBeanNotCreatedWhenDisabled() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslPropertiesConfiguration.class, DslWorkerConfiguration.class)
            .withPropertyValues("dsl.worker.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(Worker.class));
  }

  @Test
  void workerBeanNotCreatedWhenPropertyAbsent() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslPropertiesConfiguration.class, DslWorkerConfiguration.class)
            .run(ctx -> assertThat(ctx).doesNotHaveBean(Worker.class));
  }

  @Test
  void smartLifecycleHooksDriveFactoryStartOnRefreshAndShutdownOnClose() {
    WorkerFactory mockFactory = mock(WorkerFactory.class);
    lenient().when(mockFactory.newWorker(anyString())).thenReturn(mock(Worker.class));

    TestableDslWorkerConfiguration.OVERRIDE_FACTORY = mockFactory;
    try {
      AtomicReference<SmartLifecycle> lifecycleRef = new AtomicReference<>();
      new ApplicationContextRunner()
              .withUserConfiguration(DslPropertiesConfiguration.class,
                      TestableDslWorkerConfiguration.class, MockWorkflowClient.class)
              .withPropertyValues("dsl.worker.enabled=true")
              .run(ctx -> {
                assertThat(ctx).hasSingleBean(Worker.class);
                assertThat(ctx).hasSingleBean(WorkerFactory.class);
                SmartLifecycle lifecycle = ctx.getBean(SmartLifecycle.class);
                lifecycleRef.set(lifecycle);
                assertThat(lifecycle.isRunning()).isTrue();
              });
      verify(mockFactory, atLeastOnce()).start();
      verify(mockFactory, atLeastOnce()).shutdown();
      assertThat(lifecycleRef.get().isRunning()).isFalse();
    } finally {
      TestableDslWorkerConfiguration.OVERRIDE_FACTORY = null;
    }
  }

  @Test
  void workerFactoryLifecycleRunningFlagIsVisibleAcrossManyObservers() throws Exception {
    WorkerFactory mockFactory = mock(WorkerFactory.class);
    lenient().when(mockFactory.newWorker(anyString())).thenReturn(mock(Worker.class));

    TestableDslWorkerConfiguration.OVERRIDE_FACTORY = mockFactory;
    try {
      new ApplicationContextRunner()
              .withUserConfiguration(DslPropertiesConfiguration.class,
                      TestableDslWorkerConfiguration.class, MockWorkflowClient.class)
              .withPropertyValues("dsl.worker.enabled=true")
              .run(ctx -> {
                SmartLifecycle lifecycle = ctx.getBean(SmartLifecycle.class);
                int readers = Runtime.getRuntime().availableProcessors() * 2;
                CountDownLatch done = new CountDownLatch(
                        readers);
                ExecutorService pool = Executors
                        .newFixedThreadPool(readers);
                try {
                  for (int i = 0; i < readers; i++) {
                    pool.submit(() -> {
                      try {
                        for (int j = 0; j < 1_000; j++) {
                          lifecycle.isRunning();
                        }
                      } finally {
                        done.countDown();
                      }
                    });
                  }
                  done.await();
                } finally {
                  pool.shutdownNow();
                }
                assertThat(lifecycle.isRunning()).isTrue();
              });
    } finally {
      TestableDslWorkerConfiguration.OVERRIDE_FACTORY = null;
    }
  }

  static class MockWorkflowClient {
    @Bean
    WorkflowClient workflowClient() {
      return mock(WorkflowClient.class);
    }
  }

  /**
   * Substitutes the production {@link WorkerFactory} with whatever {@link #OVERRIDE_FACTORY} points
   * at, so tests can drive {@code SmartLifecycle.start()/stop()} against a Mockito mock rather than
   * a factory that requires a live Temporal server.
   */
  static class TestableDslWorkerConfiguration extends DslWorkerConfiguration {
    static volatile WorkerFactory OVERRIDE_FACTORY;

    @Override
    protected WorkerFactory createWorkerFactory(WorkflowClient workflowClient) {
      return OVERRIDE_FACTORY;
    }
  }

  @Configuration
  @EnableConfigurationProperties(DslProperties.class)
  static class DslPropertiesConfiguration {
  }
}
