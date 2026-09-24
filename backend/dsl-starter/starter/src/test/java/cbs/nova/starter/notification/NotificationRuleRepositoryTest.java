package cbs.nova.starter.notification;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.NotificationTestApplication;
import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.entity.NotificationRuleFiringEntity;
import cbs.nova.starter.persistence.NotificationRuleFiringRepository;
import cbs.nova.starter.persistence.NotificationRuleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

/**
 * H2 round-trip tests for the notification rule + firing repositories: generated keys, paged
 * listing in match order, the engine's enabled/event-type lookup, and the append-only firing
 * search.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = cbs.nova.starter.NotificationTestApplication.class)
@Sql(scripts = {"classpath:db/migration/h2/V1__init.sql",
    "classpath:sql/truncate-dsl-events.sql",
    "classpath:sql/truncate-notification-rules.sql"})
@TestPropertySource(properties = {
    "csb.dsl.worker.enabled=false"
})
class NotificationRuleRepositoryTest {

  @Autowired
  private NotificationRuleRepository repository;

  @Autowired
  private NotificationRuleFiringRepository firingRepository;

  @Test
  void insertReturnsGeneratedIdAndFindByIdRoundTrips() {
    long id = repository.insert(rule("rule-a", true, "RunFailed", 5));

    Optional<NotificationRuleEntity> found = repository.findById(id);

    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(id);
    assertThat(found.get().name()).isEqualTo("rule-a");
    assertThat(found.get().eventType()).isEqualTo("RunFailed");
    assertThat(found.get().enabled()).isTrue();
    assertThat(found.get().priority()).isEqualTo(5);
    assertThat(found.get().rateClass()).isEqualTo("default");
    assertThat(found.get().definitionPattern()).isEqualTo("Loan*");
    assertThat(found.get().createdAt()).isNotNull();
    assertThat(found.get().updatedAt()).isNotNull();
    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void findAllOrdersByPriorityDescThenIdAscAndCounts() {
    long low = repository.insert(rule("low", true, "RunFailed", 1));
    long high = repository.insert(rule("high", true, "RunFailed", 9));
    long mid = repository.insert(rule("mid", true, "RunFailed", 5));

    var result = repository.findAll(0, 10);

    assertThat(result.total()).isEqualTo(3);
    assertThat(result.items()).extracting(NotificationRuleEntity::name)
            .containsExactly("high", "mid", "low");

    var page = repository.findAll(1, 1);
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.items()).extracting(NotificationRuleEntity::id).containsExactly(mid);
    assertThat(repository.findById(low)).isPresent();
    assertThat(repository.findById(high)).isPresent();
  }

  @Test
  void findMatchingEnabledReturnsOnlyEnabledRulesForEventTypeInMatchOrder() {
    repository.insert(rule("disabled", false, "RunFailed", 9));
    repository.insert(rule("other-type", true, "RunCompleted", 8));
    long second = repository.insert(rule("second", true, "RunFailed", 1));
    long first = repository.insert(rule("first", true, "RunFailed", 7));

    List<NotificationRuleEntity> matched = repository.findMatchingEnabled("RunFailed");

    assertThat(matched).extracting(NotificationRuleEntity::name)
            .containsExactly("first", "second");
    assertThat(matched.get(0).id()).isEqualTo(first);
    assertThat(matched.get(1).id()).isEqualTo(second);
  }

  @Test
  void updateAndDeleteRoundTrip() {
    long id = repository.insert(rule("original", true, "RunFailed", 0));
    NotificationRuleEntity existing = repository.findById(id).orElseThrow();

    repository.update(new NotificationRuleEntity(
            existing.id(), "renamed", false, existing.eventType(), existing.aggregateType(),
            existing.aggregateIdPattern(), existing.definitionPattern(), existing.status(),
            existing.actionsJson(), 3, "critical", existing.createdAt(), Instant.now()));

    NotificationRuleEntity updated = repository.findById(id).orElseThrow();
    assertThat(updated.name()).isEqualTo("renamed");
    assertThat(updated.enabled()).isFalse();
    assertThat(updated.priority()).isEqualTo(3);
    assertThat(updated.rateClass()).isEqualTo("critical");
    assertThat(updated.createdAt()).isEqualTo(existing.createdAt());

    assertThat(repository.delete(id)).isTrue();
    assertThat(repository.findById(id)).isEmpty();
    assertThat(repository.delete(id)).isFalse();
  }

  @Test
  void firingInsertAndSearchNewestFirstWithRuleIdFilter() {
    long ruleId = repository.insert(rule("rule-f", true, "RunFailed", 0));
    Instant now = Instant.now();
    firingRepository
            .insert(firing(ruleId, "rule-f", "webhook", "success", now.minusSeconds(2), 7L));
    firingRepository.insert(firing(ruleId, "rule-f", "email", "noop", now.minusSeconds(1), 0L));
    firingRepository.insert(firing(999L, "other", "webhook", "success", now, 1L));

    var all = firingRepository.search(null, 0, 10);
    assertThat(all.total()).isEqualTo(3);
    assertThat(all.items()).extracting(NotificationRuleFiringEntity::sink)
            .containsExactly("webhook", "email", "webhook");

    var filtered = firingRepository.search(ruleId, 0, 10);
    assertThat(filtered.total()).isEqualTo(2);
    assertThat(filtered.items()).allSatisfy(row -> {
      assertThat(row.ruleId()).isEqualTo(ruleId);
      assertThat(row.ruleName()).isEqualTo("rule-f");
      assertThat(row.eventId()).isEqualTo(42L);
      assertThat(row.id()).isNotNull();
    });
  }

  private static NotificationRuleEntity rule(String name, boolean enabled, String eventType,
          int priority) {
    Instant now = Instant.now();
    return new NotificationRuleEntity(null, name, enabled, eventType, "run", null, "Loan*",
            null, "[{\"sink\":\"webhook\",\"url\":\"https://example.com/hook\"}]", priority,
            "default", now, now);
  }

  private static NotificationRuleFiringEntity firing(long ruleId, String ruleName, String sink,
          String outcome, Instant createdAt, Long durationMs) {
    return new NotificationRuleFiringEntity(null, 42L, ruleId, ruleName, sink, outcome, null,
            durationMs, createdAt);
  }
}
