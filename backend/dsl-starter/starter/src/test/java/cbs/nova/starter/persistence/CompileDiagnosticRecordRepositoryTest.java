package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.WebhookTestApplication;
import cbs.nova.dsl.model.CompileDiagnostic;
import cbs.nova.starter.model.CompileDiagnosticRecord;
import cbs.nova.starter.model.CompileDiagnosticSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = WebhookTestApplication.class)
@Sql(scripts = {"classpath:db/migration/h2/V1__init.sql",
    "classpath:sql/truncate-dsl-compile-diagnostics.sql"})
@TestPropertySource(properties = {
    "cbs.dsl.worker.enabled=false"
})
class CompileDiagnosticRecordRepositoryTest {

  @Autowired
  private CompileDiagnosticRecordRepository repository;

  @Test
  void insertAllThenSearchRoundTripsNewestFirst() {
    Instant now = Instant.now();
    repository.insertAll(CompileDiagnosticSource.RELOAD, "def-a",
            List.of(diagnostic("error1", now.minus(2, ChronoUnit.MINUTES))));
    repository.insertAll(CompileDiagnosticSource.PUBLISH, "def-b",
            List.of(diagnostic("error2", now.minus(1, ChronoUnit.MINUTES))));
    repository.insertAll(CompileDiagnosticSource.RELOAD, "def-a",
            List.of(diagnostic("error3", now)));

    var result = repository.search(null, 0, 10);

    assertThat(result.total()).isEqualTo(3);
    assertThat(result.items()).extracting(CompileDiagnosticRecord::message)
            .containsExactly("error3", "error2", "error1");
    assertThat(result.items()).allSatisfy(r -> {
      assertThat(r.id()).isNotNull();
      assertThat(r.occurredAt()).isNotNull();
      assertThat(r.definition()).isNotBlank();
      assertThat(r.severity()).isEqualTo("error");
    });
  }

  @Test
  void definitionFilterNarrowsResultsAndTotal() {
    Instant now = Instant.now();
    repository.insertAll(CompileDiagnosticSource.RELOAD, "def-a",
            List.of(diagnostic("a1", now.minus(2, ChronoUnit.MINUTES))));
    repository.insertAll(CompileDiagnosticSource.PUBLISH, "def-b",
            List.of(diagnostic("b1", now.minus(1, ChronoUnit.MINUTES))));
    repository.insertAll(CompileDiagnosticSource.RELOAD, "def-a",
            List.of(diagnostic("a2", now)));

    var result = repository.search("def-a", 0, 10);

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).hasSize(2);
    assertThat(result.items()).extracting(CompileDiagnosticRecord::message)
            .containsExactly("a2", "a1");
  }

  @Test
  void searchAppliesLimitAndOffset() {
    Instant now = Instant.now();
    for (int i = 0; i < 5; i++) {
      repository.insertAll(CompileDiagnosticSource.RELOAD, "def-" + i,
              List.of(diagnostic("m" + i, now.minus(5 - i, ChronoUnit.MINUTES))));
    }

    var page = repository.search(null, 1, 2);

    assertThat(page.total()).isEqualTo(5);
    assertThat(page.items()).extracting(CompileDiagnosticRecord::message)
            .containsExactly("m3", "m2");
  }

  @Test
  void searchRejectsBadPagination() {
    assertThatThrownBy(() -> repository.search(null, -1, 10))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> repository.search(null, 0, 0))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void longDefinitionIsTruncatedOnInsert() {
    String longDefinition = "x".repeat(300);
    repository.insertAll(CompileDiagnosticSource.RELOAD, longDefinition,
            List.of(diagnostic("truncated", Instant.now())));

    var result = repository.search(null, 0, 10);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).definition()).hasSize(256);
  }

  private static CompileDiagnostic diagnostic(String message, Instant occurredAt) {
    return new CompileDiagnostic("file.java", 1L, 2L, message, "error", "compiler.err.expected");
  }
}
