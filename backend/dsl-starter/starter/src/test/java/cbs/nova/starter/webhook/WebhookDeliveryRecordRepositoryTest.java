package cbs.nova.starter.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.WebhookTestApplication;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = WebhookTestApplication.class)
@Sql(scripts = {"classpath:db/migration/h2/V1__init.sql",
    "classpath:sql/truncate-dsl-webhook-deliveries.sql"})
@TestPropertySource(properties = {
    "csb.dsl.worker.enabled=false"
})
class WebhookDeliveryRecordRepositoryTest {

  @Autowired
  private WebhookDeliveryRecordRepository repository;

  @Test
  void insertThenSearchRoundTripsNewestFirst() {
    Instant now = Instant.now();
    repository.insert(row("sub-1", "200", 1, now.minus(2, ChronoUnit.MINUTES), null));
    repository.insert(row("sub-2", "200", 1, now.minus(1, ChronoUnit.MINUTES), null));
    repository.insert(row("sub-1", "500", 3, now, "boom"));

    var result = repository.search(null, 0, 10);

    assertThat(result.total()).isEqualTo(3);
    assertThat(result.items()).extracting(WebhookDeliveryRecord::subscriptionId)
            .containsExactly("sub-1", "sub-2", "sub-1");
    assertThat(result.items()).allSatisfy(r -> {
      assertThat(r.id()).isNotNull();
      assertThat(r.occurredAt()).isNotNull();
      assertThat(r.eventType()).isEqualTo("run.completed");
      assertThat(r.url()).startsWith("https://example.com/");
    });
  }

  @Test
  void subscriptionIdFilterNarrowsResultsAndTotal() {
    Instant now = Instant.now();
    repository.insert(row("sub-a", "200", 1, now.minus(2, ChronoUnit.MINUTES), null));
    repository.insert(row("sub-b", "200", 1, now.minus(1, ChronoUnit.MINUTES), null));
    repository.insert(row("sub-a", "500", 3, now, "boom"));

    var result = repository.search("sub-a", 0, 10);

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).hasSize(2);
    assertThat(result.items()).extracting(WebhookDeliveryRecord::status)
            .containsExactly("500", "200");
  }

  @Test
  void searchAppliesLimitAndOffset() {
    Instant now = Instant.now();
    for (int i = 0; i < 5; i++) {
      repository.insert(row("sub-" + i, "200", 1, now.minus(5 - i, ChronoUnit.MINUTES), null));
    }

    var page = repository.search(null, 1, 2);

    assertThat(page.total()).isEqualTo(5);
    assertThat(page.items()).extracting(WebhookDeliveryRecord::subscriptionId)
            .containsExactly("sub-3", "sub-2");
  }

  @Test
  void searchRejectsBadPagination() {
    assertThatThrownBy(() -> repository.search(null, -1, 10))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> repository.search(null, 0, 0))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void longUrlIsTruncatedOnInsert() {
    String longUrl = "https://example.com/" + "x".repeat(3000);
    repository.insert(new WebhookDeliveryRecord(null, Instant.now(), "sub-long", "run.completed",
            longUrl, "200", 1, null, 10L));

    var result = repository.search("sub-long", 0, 10);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).url()).hasSize(2048);
  }

  private static WebhookDeliveryRecord row(String subscriptionId, String status, int attempts,
          Instant occurredAt, String lastError) {
    return new WebhookDeliveryRecord(null, occurredAt, subscriptionId, "run.completed",
            "https://example.com/" + subscriptionId, status, attempts, lastError, 100L);
  }

}
