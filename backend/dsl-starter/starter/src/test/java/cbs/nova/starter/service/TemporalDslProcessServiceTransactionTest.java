package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.service.TemporalDslProcessService.ProcessRun;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.events.DomainEventListener;
import cbs.nova.starter.events.sink.DomainEventSink;
import cbs.nova.starter.persistence.DslEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Sql(scripts = {
    "classpath:db/migration/h2/V1__init.sql",
    "classpath:sql/truncate-dsl-tables.sql"
})
@TestPropertySource(properties = {
    "cbs.nova.process.async-db-save=false",
    "cbs.dsl.worker.enabled=false"
})
class TemporalDslProcessServiceTransactionTest {

  @Autowired
  private DslRunRepository runRepository;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager().registerProcess(
            Dsl.process("OkFlow").execute(ctx -> Result.success("ok")).build());
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void updateFinishedIsRolledBackWhenEventInsertFails() {
    TemporalDslProcessService service = newService();

    ProcessRun run = service.runProcess("OkFlow", Map.of(), (String) null);
    assertThatThrownBy(run.result()::join)
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(DataAccessResourceFailureException.class);

    DslRun persisted = runRepository.findByRunId(run.runId()).orElseThrow();
    assertThat(persisted.status()).isEqualTo(DslRunStatus.RUNNING.name());
    assertThat(persisted.output()).isEqualTo(StarterConstants.EMPTY_OUTPUT_JSON);
    assertThat(persisted.error()).isNull();
  }

  private TemporalDslProcessService newService() {
    return new TemporalDslProcessService(
            runRepository,
            new ObjectMapper(),
            sameThreadExecutor(),
            disabledScheduledExecutor(),
            Duration.ofSeconds(30),
            Duration.ofMinutes(5),
            false,
            Long.MAX_VALUE,
            new SimpleMeterRegistry(),
            new RunIdentityResolver(),
            Optional.empty(),
            OpenTelemetry.noop(),
            ofPublisher(failingPublisher()),
            ofTransactionTemplate(transactionTemplate));
  }

  private DomainEventPublisher failingPublisher() {
    DslEventRepository repository = mock(DslEventRepository.class);
    doThrow(new DataAccessResourceFailureException("event insert failed"))
            .when(repository).insert(argThat(row -> "RunCompleted".equals(row.eventType())));
    return new DomainEventPublisher(repository, new ObjectMapper(),
            EmptyObjectProvider.of(DomainEventListener.class),
            EmptyObjectProvider.of(DomainEventSink.class));
  }

  private static ObjectProvider<DomainEventPublisher> ofPublisher(DomainEventPublisher publisher) {
    return new ObjectProvider<>() {
      @Override
      public DomainEventPublisher getObject() {
        return publisher;
      }

      @Override
      public DomainEventPublisher getIfAvailable() {
        return publisher;
      }

      @Override
      public DomainEventPublisher getIfUnique() {
        return publisher;
      }
    };
  }

  private static ObjectProvider<TransactionTemplate> ofTransactionTemplate(TransactionTemplate tx) {
    return new ObjectProvider<>() {
      @Override
      public TransactionTemplate getObject() {
        return tx;
      }

      @Override
      public TransactionTemplate getIfAvailable() {
        return tx;
      }

      @Override
      public TransactionTemplate getIfUnique() {
        return tx;
      }
    };
  }

  private static @NonNull ThreadPoolTaskExecutor sameThreadExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor() {
      @Override
      public void execute(@NonNull Runnable command) {
        command.run();
      }
    };
    exec.setCorePoolSize(1);
    exec.setMaxPoolSize(1);
    exec.setQueueCapacity(0);
    exec.setThreadNamePrefix("cbs-nova-dsl-tx-test-");
    exec.initialize();
    return exec;
  }

  private static @NonNull ScheduledExecutorService disabledScheduledExecutor() {
    ThreadFactory tf = r -> {
      Thread t = new Thread(r, "cbs-nova-dsl-healthcheck-disabled");
      t.setDaemon(true);
      return t;
    };
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(tf);
    exec.shutdownNow();
    return exec;
  }

  @SpringBootApplication(scanBasePackages = "cbs.nova.starter")
  static class TestApplication {
    public static void main(String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }
}
