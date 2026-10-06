package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.events.DomainEventListener;
import cbs.nova.starter.events.sink.DomainEventSink;
import cbs.nova.starter.persistence.DslEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class TemporalDslProcessServiceAsyncFailureTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final DslRunRepository runRepository = new InMemoryDslRunRepository(
          InMemoryDslRunRepository.NO_OP_EVICTION);
  private final ObjectMapper objectMapper = new ObjectMapper();

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
  void asyncDbWriteFailureLogsRunIdAndEventTypeAndIncrementsCounter(CapturedOutput output) {
    TemporalDslProcessService service = new TemporalDslProcessService(
            runRepository,
            objectMapper,
            sameThreadExecutor(),
            disabledScheduledExecutor(),
            Duration.ofSeconds(30),
            Duration.ofMinutes(5),
            true,
            Long.MAX_VALUE,
            meterRegistry,
            new RunIdentityResolver(),
            Optional.empty(),
            OpenTelemetry.noop(),
            ofPublisher(failingPublisher()),
            ofTransactionTemplate(newTransactionTemplate()));

    service.runProcess("OkFlow", Map.of(), (String) null).result().join();

    assertThat(output.getOut()).contains("Async DSL DB write failed");
    assertThat(output.getOut()).contains("eventType=RunStarted");
    assertThat(meterRegistry.counter(StarterConstants.DB_WRITE_FAILED_COUNTER).count())
            .isEqualTo(2.0);
  }

  private DomainEventPublisher failingPublisher() {
    DslEventRepository repository = mock(DslEventRepository.class);
    doThrow(new org.springframework.dao.DataAccessResourceFailureException("db gone"))
            .when(repository).insert(any());
    return new DomainEventPublisher(repository, objectMapper,
            EmptyObjectProvider.of(DomainEventListener.class),
            EmptyObjectProvider.of(DomainEventSink.class));
  }

  private TransactionTemplate newTransactionTemplate() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.h2.Driver");
    dataSource.setUrl("jdbc:h2:mem:testdb-async;DB_CLOSE_DELAY=-1");
    return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
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

  private static ObjectProvider<TransactionTemplate> ofTransactionTemplate(
          TransactionTemplate tx) {
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
    exec.setThreadNamePrefix("cbs-nova-dsl-async-failure-");
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
}
