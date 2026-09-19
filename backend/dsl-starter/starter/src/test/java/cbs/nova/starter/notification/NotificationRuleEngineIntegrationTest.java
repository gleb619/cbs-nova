package cbs.nova.starter.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.NotificationTestApplication;
import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.entity.NotificationRuleFiringEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.EventFilterDto;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.model.NotificationRuleModels.NotificationRuleDto;
import cbs.nova.starter.model.NotificationRuleModels.TestEventResponse;
import cbs.nova.starter.persistence.NotificationRuleFiringRepository;
import cbs.nova.starter.service.DomainEventPublisher;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

/**
 * End-to-end tests for the T565 notification rules engine: a real {@link DomainEventPublisher}
 * publish against H2-backed rule/firing stores, with a WireMock endpoint standing in for the
 * webhook target.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = cbs.nova.starter.NotificationTestApplication.class)
@Sql(scripts = {"classpath:db/migration/h2/V1__init.sql",
    "classpath:db/migration/h2/V2__dsl_audit.sql",
    "classpath:db/migration/h2/V7__dsl_events.sql",
    "classpath:db/migration/h2/V9__notification_rules.sql",
    "classpath:sql/truncate-dsl-audit.sql",
    "classpath:sql/truncate-dsl-events.sql",
    "classpath:sql/truncate-notification-rules.sql"})
@TestPropertySource(properties = {
    "csb.dsl.worker.enabled=false",
    "cbs.nova.dsl.webhooks.allow-plain-http=true",
    "cbs.nova.dsl.webhooks.max-retries=1"
})
class NotificationRuleEngineIntegrationTest {

  private static final String HOOK_PATH = "/hook";

  @Autowired
  private NotificationRuleService ruleService;

  @Autowired
  private NotificationRuleEngine ruleEngine;

  @Autowired
  private DomainEventPublisher publisher;

  @Autowired
  private NotificationRuleFiringRepository firingRepository;

  private WireMockServer wireMock;

  @BeforeEach
  void setUp() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
    wireMock.stubFor(post(HOOK_PATH).willReturn(ok()));
  }

  @AfterEach
  void tearDown() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @Test
  void matchingRunFailedEventDispatchesWebhookAndRecordsFiringAudit() {
    NotificationRuleEntity rule = ruleService.create(ruleDto("loan-failures", true,
            new EventFilterDto("RunFailed", null, null, "Loan*", null),
            List.of(new NotificationActionDto("webhook", baseUrl() + HOOK_PATH, "topsecret", null)),
            10));

    long eventRowId = publisher.publish(runFailed("run-1", "LoanOrigination"));

    await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(
                    wireMock.findAll(postRequestedFor(urlEqualTo(HOOK_PATH)))).hasSize(1));

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      var result = firingRepository.search(null, 0, 10);
      assertThat(result.total()).isEqualTo(1);
      NotificationRuleFiringEntity firing = result.items().get(0);
      assertThat(firing.eventId()).isEqualTo(eventRowId);
      assertThat(firing.ruleId()).isEqualTo(rule.id());
      assertThat(firing.ruleName()).isEqualTo("loan-failures");
      assertThat(firing.sink()).isEqualTo("webhook");
      assertThat(firing.outcome()).isEqualTo("success");
    });
  }

  @Test
  void disabledRuleDoesNotDispatchAndStaysPresent() {
    NotificationRuleEntity rule = ruleService.create(ruleDto("disabled-rule", true,
            new EventFilterDto("RunFailed", null, null, null, null),
            List.of(new NotificationActionDto("webhook", baseUrl() + HOOK_PATH, null, null)), 0));

    NotificationRuleEntity disabled = ruleService.setEnabled(rule.id(), false).orElseThrow();
    assertThat(disabled.enabled()).isFalse();

    publisher.publish(runFailed("run-2", "LoanOrigination"));

    await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1)).untilAsserted(
            () -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo(HOOK_PATH)))).isEmpty());

    assertThat(firingRepository.search(null, 0, 10).total()).isZero();
    assertThat(ruleService.findById(rule.id())).isPresent();
  }

  @Test
  void nonMatchingEventsDispatchNothing() {
    ruleService.create(ruleDto("loan-only", true,
            new EventFilterDto("RunFailed", null, null, "Loan*", null),
            List.of(new NotificationActionDto("webhook", baseUrl() + HOOK_PATH, null, null)), 0));

    publisher.publish(new DomainEvent.RunCompleted("run-3", "LoanOrigination",
            DslRunStatus.COMPLETED, null, null, Instant.now(), Instant.now(), null, null));
    publisher.publish(runFailed("run-4", "OtherProcess"));

    await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1)).untilAsserted(
            () -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo(HOOK_PATH)))).isEmpty());

    assertThat(firingRepository.search(null, 0, 10).total()).isZero();
  }

  @Test
  void emailSinkRecordsNoopFiring() {
    NotificationRuleEntity rule = ruleService.create(ruleDto("email-rule", true,
            new EventFilterDto("RunFailed", null, null, null, null),
            List.of(new NotificationActionDto("email", null, null, "ops@example.com")), 0));

    long eventRowId = publisher.publish(runFailed("run-5", "AnyProcess"));

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      var result = firingRepository.search(null, 0, 10);
      assertThat(result.total()).isEqualTo(1);
      NotificationRuleFiringEntity firing = result.items().get(0);
      assertThat(firing.eventId()).isEqualTo(eventRowId);
      assertThat(firing.ruleId()).isEqualTo(rule.id());
      assertThat(firing.sink()).isEqualTo("email");
      assertThat(firing.outcome()).isEqualTo("noop");
    });
  }

  @Test
  void testEventRunsMatchingInlineWithoutPersistingAnything() {
    NotificationRuleEntity rule = ruleService.create(ruleDto("testable", true,
            new EventFilterDto("RunFailed", null, null, "Loan*", null),
            List.of(new NotificationActionDto("email", null, null, "ops@example.com")), 0));

    TestEventResponse matched = ruleEngine.testEvent(runFailed("run-t", "LoanX"));
    assertThat(matched.matchedRuleIds()).containsExactly(rule.id());
    assertThat(matched.fireResults()).hasSize(1);
    assertThat(matched.fireResults().get(0).outcome()).isEqualTo("noop");

    TestEventResponse unmatched = ruleEngine.testEvent(runFailed("run-t2", "Other"));
    assertThat(unmatched.matchedRuleIds()).isEmpty();
    assertThat(unmatched.fireResults()).isEmpty();

    assertThat(firingRepository.search(null, 0, 10).total()).isZero();
  }

  private String baseUrl() {
    return "http://localhost:" + wireMock.port();
  }

  private static DomainEvent.RunFailed runFailed(String runId, String processName) {
    Instant now = Instant.now();
    return new DomainEvent.RunFailed(runId, processName, DslRunStatus.FAILED, "boom", now, now,
            null, null);
  }

  private static NotificationRuleDto ruleDto(String name, boolean enabled,
          EventFilterDto eventFilter, List<NotificationActionDto> actions, int priority) {
    return new NotificationRuleDto(null, name, enabled, eventFilter, actions, priority,
            "default", null, null);
  }
}
