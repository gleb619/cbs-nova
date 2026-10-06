package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.events.DomainEventListener;
import cbs.nova.starter.events.sink.DomainEventSink;
import cbs.nova.starter.persistence.DslEventRepository;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(DomainEventPublisherAfterCommitTest.TestConfig.class)
@Sql(scripts = {
    "classpath:db/migration/h2/V1__init.sql",
    "classpath:sql/truncate-dsl-tables.sql"
})
class DomainEventPublisherAfterCommitTest {

  @Autowired
  private DomainEventPublisher publisher;

  @Autowired
  private DslEventRepository repository;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private RecordingSink sink;

  @Autowired
  private RecordingListener listener;

  @BeforeEach
  void reset() {
    sink.reset();
    listener.reset();
  }

  @Test
  void fanOutRunsAfterCommitOutsideTransaction() {
    transactionTemplate.executeWithoutResult(status -> {
      publisher.publish(new DomainEvent.RunStarted(
              "run-ac", "proc", null, Instant.now(), null));
    });

    assertThat(sink.received()).isTrue();
    assertThat(sink.txActive()).isFalse();
    assertThat(listener.received()).isTrue();
    assertThat(listener.txActive()).isFalse();
    assertThat(repository.search(null, null, "run-ac", null, null, 0, 10).total())
            .isEqualTo(1L);
  }

  @Test
  void fanOutRunsImmediatelyWhenNoTransaction() {
    publisher.publish(new DomainEvent.RunStarted(
            "run-no-tx", "proc", null, Instant.now(), null));

    assertThat(sink.received()).isTrue();
    assertThat(sink.txActive()).isFalse();
    assertThat(listener.received()).isTrue();
    assertThat(listener.txActive()).isFalse();
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    RecordingSink recordingSink() {
      return new RecordingSink();
    }

    @Bean
    RecordingListener recordingListener() {
      return new RecordingListener();
    }
  }

  static class RecordingSink implements DomainEventSink {

    private final AtomicBoolean received = new AtomicBoolean(false);
    private final AtomicBoolean txActive = new AtomicBoolean(false);

    @Override
    public void onEvent(DomainEvent event, long eventRowId) {
      txActive.set(TransactionSynchronizationManager.isActualTransactionActive());
      received.set(true);
    }

    void reset() {
      received.set(false);
      txActive.set(false);
    }

    boolean received() {
      return received.get();
    }

    boolean txActive() {
      return txActive.get();
    }
  }

  static class RecordingListener implements DomainEventListener {

    private final AtomicBoolean received = new AtomicBoolean(false);
    private final AtomicBoolean txActive = new AtomicBoolean(false);

    @Override
    public void onEvent(DomainEvent event, long eventRowId) {
      txActive.set(TransactionSynchronizationManager.isActualTransactionActive());
      received.set(true);
    }

    void reset() {
      received.set(false);
      txActive.set(false);
    }

    boolean received() {
      return received.get();
    }

    boolean txActive() {
      return txActive.get();
    }
  }

  @SpringBootApplication(scanBasePackages = "cbs.nova.starter")
  static class TestApplication {
    public static void main(String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }
}
