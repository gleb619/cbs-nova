package cbs.nova.starter.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.webhook.WebhookDeliveryTestSupport.H2Repository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link WebhookDeliveryRecordRepository#purgeOlderThan} against an in-memory
 * H2 {@code dsl_webhook_deliveries} table. Backed by the
 * {@code idx_dsl_webhook_deliveries_occurred_at} index — no Spring context, just the Spring Data
 * JDBC repository over the H2 schema.
 */
class WebhookDeliveryRecordRepositoryPurgeIntegrationTest {

  private H2Repository harness;

  @BeforeEach
  void setUp() {
    harness = WebhookDeliveryTestSupport.h2WithSearch();
  }

  @Test
  void purgeOlderThanWithCutoffAfterAllRowsDeletesEverything() {
    Instant now = Instant.now();
    harness.insert(row("sub-1", "200", now.minus(100, ChronoUnit.DAYS)));
    harness.insert(row("sub-2", "500", now.minus(1, ChronoUnit.DAYS)));

    int purged = harness.repository().purgeOlderThan(now.plus(1, ChronoUnit.HOURS), 100);

    assertThat(purged).isEqualTo(2);
    assertThat(harness.search(null, 0, 10).total()).isZero();
  }

  @Test
  void purgeOlderThanDeletesOnlyOldRows() {
    Instant now = Instant.now();
    harness.insert(row("sub-old", "200", now.minus(100, ChronoUnit.DAYS)));
    harness.insert(row("sub-old", "500", now.minus(45, ChronoUnit.DAYS)));
    harness.insert(row("sub-recent", "200", now.minus(7, ChronoUnit.DAYS)));
    harness.insert(row("sub-recent", "200", now.minus(1, ChronoUnit.DAYS)));

    int purged = harness.repository().purgeOlderThan(now.minus(30, ChronoUnit.DAYS), 100);

    assertThat(purged).isEqualTo(2);
    assertThat(harness.search(null, 0, 10).total()).isEqualTo(2L);
  }

  @Test
  void purgeOlderThanUsesStrictLessThan() {
    Instant now = Instant.now();
    Instant cutoff = now.minus(10, ChronoUnit.DAYS);
    harness.insert(row("sub-old", "200", cutoff.minus(1, ChronoUnit.MINUTES)));
    harness.insert(row("sub-recent", "200", cutoff));

    int purged = harness.repository().purgeOlderThan(cutoff, 100);

    assertThat(purged).isEqualTo(1);
    assertThat(harness.search(null, 0, 10).total()).isEqualTo(1L);
  }

  @Test
  void purgeOlderThanRespectsBatchSizeAcrossMultiplePasses() {
    Instant now = Instant.now();
    for (int i = 0; i < 7; i++) {
      harness.insert(row("sub-old-" + i, "200", now.minus(50 + i, ChronoUnit.DAYS)));
    }
    harness.insert(row("sub-recent", "200", now.minus(1, ChronoUnit.DAYS)));

    int total = 0;
    int batchSize = 2;
    Instant cutoff = now.minus(30, ChronoUnit.DAYS);
    while (true) {
      int deleted = harness.repository().purgeOlderThan(cutoff, batchSize);
      if (deleted == 0) {
        break;
      }
      total += deleted;
      if (deleted < batchSize) {
        break;
      }
    }

    assertThat(total).isEqualTo(7);
    assertThat(harness.search(null, 0, 100).total()).isEqualTo(1L);
  }

  @Test
  void purgeOlderThanWithEmptyTableReturnsZero() {
    int purged = harness.repository().purgeOlderThan(Instant.now(), 100);
    assertThat(purged).isZero();
  }

  private static WebhookDeliveryRecord row(String subscriptionId, String status,
          Instant occurredAt) {
    return new WebhookDeliveryRecord(null, occurredAt, subscriptionId, "run.completed",
            "https://example.com/" + subscriptionId, status, 1, null, 100L);
  }
}
