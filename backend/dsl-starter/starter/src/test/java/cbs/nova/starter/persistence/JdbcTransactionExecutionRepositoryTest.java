package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionExecution;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Sql(scripts = {"classpath:db/migration/h2/V1__init.sql", "classpath:sql/truncate-dsl-tables.sql"})
@TestPropertySource(properties = {
    "csb.dsl.worker.enabled=false"
})
class JdbcTransactionExecutionRepositoryTest {

  @Autowired
  private TransactionExecutionRepository repository;

  @Test
  void saveAndFindByRunIdRoundTrip() {
    TransactionExecution execution = execution("run-1", "CreateOrder", Map.of("sku", "ABC"));

    repository.save(execution);
    List<TransactionExecution> found = repository.findByRunId("run-1");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).runId()).isEqualTo("run-1");
    assertThat(found.get(0).transactionName()).isEqualTo("CreateOrder");
    assertThat(found.get(0).executedAt()).isEqualTo(Instant.parse("2026-07-19T00:00:00Z"));
    assertThat(found.get(0).input()).isEqualTo(Map.of("sku", "ABC"));
  }

  @Test
  void saveAndFindByRunIdWithNullInput() {
    TransactionExecution execution = execution("run-2", "ReserveStock", null);

    repository.save(execution);
    List<TransactionExecution> found = repository.findByRunId("run-2");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).input()).isNull();
  }

  @Test
  void findByRunIdReturnsMultipleExecutionsMostRecentFirst() {
    TransactionExecution a = execution("run-3", "StepA", Map.of("order", 1));
    TransactionExecution b = execution("run-3", "StepB", Map.of("order", 2));
    TransactionExecution c = execution("run-3", "StepC", null);

    repository.save(a);
    repository.save(b);
    repository.save(c);

    List<TransactionExecution> found = repository.findByRunId("run-3");

    assertThat(found).hasSize(3);
    assertThat(found).extracting(TransactionExecution::transactionName)
            .containsExactly("StepC", "StepB", "StepA");
  }

  @Test
  void findByRunIdReturnsEmptyWhenMissing() {
    assertThat(repository.findByRunId("missing")).isEmpty();
  }

  @Test
  void deleteByRunIdRemovesOnlyMatchingRunId() {
    repository.save(execution("run-4", "CreateOrder", Map.of("sku", "A")));
    repository.save(execution("run-4", "ReserveStock", null));
    repository.save(execution("run-5", "CreateOrder", Map.of("sku", "B")));

    repository.deleteByRunId("run-4");

    assertThat(repository.findByRunId("run-4")).isEmpty();
    assertThat(repository.findByRunId("run-5")).hasSize(1);
  }

  @Test
  void deleteByRunIdIsNoOpForMissingRunId() {
    repository.deleteByRunId("missing");

    assertThat(repository.findByRunId("missing")).isEmpty();
  }

  @Test
  void deleteByRunIdsRemovesOnlyMatchingRunIds() {
    repository.save(execution("run-6", "CreateOrder", Map.of("sku", "A")));
    repository.save(execution("run-6", "ReserveStock", null));
    repository.save(execution("run-7", "CreateOrder", Map.of("sku", "B")));
    repository.save(execution("run-8", "CreateOrder", Map.of("sku", "C")));

    int deleted = repository.deleteByRunIds(List.of("run-6", "run-8"));

    assertThat(deleted).isEqualTo(3);
    assertThat(repository.findByRunId("run-6")).isEmpty();
    assertThat(repository.findByRunId("run-7")).hasSize(1);
    assertThat(repository.findByRunId("run-8")).isEmpty();
  }

  @Test
  void deleteByRunIdsIsNoOpForEmptyCollection() {
    repository.save(execution("run-9", "CreateOrder", null));

    int deleted = repository.deleteByRunIds(List.of());

    assertThat(deleted).isZero();
    assertThat(repository.findByRunId("run-9")).hasSize(1);
  }

  private static TransactionExecution execution(String runId, String transactionName,
          Object input) {
    return new TransactionExecution(runId, transactionName, input,
            Instant.parse("2026-07-19T00:00:00Z"));
  }

  @SpringBootApplication(scanBasePackages = "cbs.nova.starter")
  static class TestApplication {
    public static void main(String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }
}
