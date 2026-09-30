package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.AuditTestSupport.Harness;
import cbs.nova.starter.model.DslAudit;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link DslAuditStore#purgeOlderThan(Instant, int)} against an in-memory H2
 * {@code dsl_audit} table. Backed by the {@code idx_dsl_audit_occurred_at} index — no Spring
 * context, just the Spring Data JDBC repository over the H2 schema.
 */
class DslAuditStorePurgeIntegrationTest {

  private Harness harness;

  @BeforeEach
  void setUp() {
    harness = AuditTestSupport.h2();
  }

  @Test
  void purgeOlderThanWithCutoffAfterAllRowsDeletesEverything() {
    Instant now = Instant.now();
    harness.append(row(now.minus(100, ChronoUnit.DAYS)));
    harness.append(row(now.minus(1, ChronoUnit.DAYS)));

    int purged = harness.store().purgeOlderThan(now.plus(1, ChronoUnit.HOURS), 100);

    assertThat(purged).isEqualTo(2);
    assertThat(harness.service().search(null, 0, 10).total()).isZero();
  }

  @Test
  void purgeOlderThanDeletesOnlyOldRows() {
    Instant now = Instant.now();
    harness.append(row(now.minus(100, ChronoUnit.DAYS)));
    harness.append(row(now.minus(95, ChronoUnit.DAYS)));
    harness.append(row(now.minus(7, ChronoUnit.DAYS)));
    harness.append(row(now.minus(1, ChronoUnit.DAYS)));

    int purged = harness.store().purgeOlderThan(now.minus(30, ChronoUnit.DAYS), 100);

    assertThat(purged).isEqualTo(2);
    DslAuditSearchResult remaining = harness.service().search(null, 0, 10);
    assertThat(remaining.total()).isEqualTo(2L);
    assertThat(remaining.items()).extracting(DslAudit::target)
            .allMatch(target -> target.startsWith("t-recent"));
  }

  @Test
  void purgeOlderThanUsesStrictLessThan() {
    Instant now = Instant.now();
    // truncate: the DB column has coarser precision than Instant.now(), which would push the
    // boundary row below the cutoff
    Instant cutoff = now.minus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    harness.append(row(cutoff.minus(1, ChronoUnit.MINUTES)));
    harness.append(row(cutoff));

    int purged = harness.store().purgeOlderThan(cutoff, 100);

    assertThat(purged).isEqualTo(1);
    DslAuditSearchResult remaining = harness.service().search(null, 0, 10);
    assertThat(remaining.total()).isEqualTo(1L);
    assertThat(remaining.items().get(0).target()).startsWith("t-recent");
  }

  @Test
  void purgeOlderThanRespectsBatchSizeAcrossMultiplePasses() {
    Instant now = Instant.now();
    for (int i = 0; i < 7; i++) {
      harness.append(row(now.minus(50 + i, ChronoUnit.DAYS)));
    }
    harness.append(row(now.minus(1, ChronoUnit.DAYS)));

    int total = 0;
    int batchSize = 2;
    Instant cutoff = now.minus(30, ChronoUnit.DAYS);
    while (true) {
      int deleted = harness.store().purgeOlderThan(cutoff, batchSize);
      if (deleted == 0) {
        break;
      }
      total += deleted;
      if (deleted < batchSize) {
        break;
      }
    }

    assertThat(total).isEqualTo(7);
    assertThat(harness.service().search(null, 0, 100).total()).isEqualTo(1L);
  }

  @Test
  void purgeOlderThanWithEmptyTableReturnsZero() {
    int purged = harness.store().purgeOlderThan(Instant.now(), 100);
    assertThat(purged).isZero();
  }

  @Test
  void purgeOlderThanRejectsNonPositiveBatchSize() {
    Instant now = Instant.now();
    assertThatThrownBy(() -> harness.store().purgeOlderThan(now, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batchSize");
    assertThatThrownBy(() -> harness.store().purgeOlderThan(now, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batchSize");
  }

  private static DslAudit row(Instant occurredAt) {
    boolean isOld = occurredAt.isBefore(Instant.now().minus(30, ChronoUnit.DAYS));
    return new DslAudit(null, occurredAt, "op-test", "DEFINITION_PUBLISH",
            isOld ? "t-old" : "t-recent-" + System.nanoTime(),
            null, "SUCCESS", null);
  }
}
