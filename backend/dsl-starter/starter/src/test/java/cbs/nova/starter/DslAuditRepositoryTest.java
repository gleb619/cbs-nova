package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.model.DslAudit;
import cbs.nova.starter.persistence.DslAuditStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = DslAuditRepositoryTest.TestApplication.class)
@Sql(scripts = {"classpath:db/migration/h2/V1__init.sql",
    "classpath:sql/truncate-dsl-audit.sql"})
@TestPropertySource(properties = {
    "csb.dsl.worker.enabled=false"
})
class DslAuditRepositoryTest {

  @Autowired
  private DslAuditStore store;

  @Test
  void insertThenSearchRoundTripsNewestFirst() {
    Instant now = Instant.now();
    store.append(
            row("op-1", "DEFINITION_PUBLISH", "LoanFlow", now.minus(2, ChronoUnit.MINUTES)));
    store.append(row("op-2", "DRAFT_WRITE", "LoanFlow", now.minus(1, ChronoUnit.MINUTES)));
    store.append(row("op-3", "DEFINITION_PUBLISH", "CreditFlow", now));

    var result = store.search(null, 0, 10);

    assertThat(result.total()).isEqualTo(3);
    assertThat(result.items()).extracting(DslAudit::actor)
            .containsExactly("op-3", "op-2", "op-1");
    assertThat(result.items()).allSatisfy(r -> {
      assertThat(r.id()).isNotNull();
      assertThat(r.occurredAt()).isNotNull();
      assertThat(r.outcome()).isEqualTo("SUCCESS");
      assertThat(r.detailsJson()).isEqualTo("{\"n\":1}");
    });
  }

  @Test
  void actionFilterNarrowsResultsAndTotal() {
    Instant now = Instant.now();
    store.append(
            row("op-1", "DEFINITION_PUBLISH", "LoanFlow", now.minus(2, ChronoUnit.MINUTES)));
    store.append(row("op-2", "DRAFT_WRITE", "LoanFlow", now.minus(1, ChronoUnit.MINUTES)));
    store.append(row("op-3", "DEFINITION_PUBLISH", "CreditFlow", now));

    var result = store.search("DEFINITION_PUBLISH", 0, 10);

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).hasSize(2);
    assertThat(result.items()).extracting(DslAudit::target)
            .containsExactly("CreditFlow", "LoanFlow");
  }

  @Test
  void searchAppliesLimitAndOffset() {
    Instant now = Instant.now();
    for (int i = 0; i < 5; i++) {
      store.append(row("op-" + i, "DRAFT_WRITE", "d" + i, now.minus(5 - i, ChronoUnit.MINUTES)));
    }

    var page = store.search(null, 1, 2);

    assertThat(page.total()).isEqualTo(5);
    assertThat(page.items()).extracting(DslAudit::target)
            .containsExactly("d3", "d2");
  }

  @Test
  void searchRejectsBadPagination() {
    assertThatThrownBy(() -> store.search(null, -1, 10))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.search(null, 0, 0))
            .isInstanceOf(IllegalArgumentException.class);
  }

  private static DslAudit row(String actor, String action, String target,
          Instant occurredAt) {
    return new DslAudit(null, occurredAt, actor, action, target, null, "SUCCESS",
            "{\"n\":1}");
  }

  @SpringBootApplication
  static class TestApplication {
  }
}
