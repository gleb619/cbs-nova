package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.entity.DslEventEntity;
import java.time.Instant;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Live H2 tests for {@link DslEventRepository} against the V7 migration. Pins the contract that the
 * four filter dimensions (event_type, aggregate_type+id, correlation_id) all narrow results
 * correctly, that the created_at index supports newest-first ordering, and that
 * {@code offset}/{@code limit} pagination is bounded by MAX(1) / non-negative rules.
 */
class DslEventRepositoryTest {

  private NamedParameterJdbcTemplate jdbcTemplate;
  private DslEventRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:events-" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    ScriptUtils.executeSqlScript(dataSource.getConnection(),
            new ClassPathResource("db/migration/h2/V7__dsl_events.sql"));
    jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    repository = new DslEventRepository(jdbcTemplate);
  }

  @Test
  void insertThenSearchRoundTripsNewestFirst() {
    Instant now = Instant.now();
    repository.insert(row("RunStarted", "run", "run-1",
            now.minus(2, java.time.temporal.ChronoUnit.MINUTES), null));
    repository.insert(row("RunCompleted", "run", "run-1",
            now.minus(1, java.time.temporal.ChronoUnit.MINUTES), null));
    repository.insert(row("DraftPublished", "definition", "def-A", now, null));

    var result = repository.search(null, null, null, null, null, 0, 10);

    assertThat(result.total()).isEqualTo(3);
    assertThat(result.items()).extracting(DslEventEntity::aggregateId)
            .containsExactly("def-A", "run-1", "run-1");
    assertThat(result.items()).allSatisfy(r -> {
      assertThat(r.id()).isNotNull();
      assertThat(r.schemaVersion()).isEqualTo(1);
      assertThat(r.createdAt()).isNotNull();
      assertThat(r.payloadJson()).isEqualTo("{\"k\":1}");
    });
  }

  @Test
  void eventTypeFilterNarrowsResultsAndTotal() {
    Instant now = Instant.now();
    repository.insert(row("RunStarted", "run", "run-1", now, null));
    repository.insert(row("RunCompleted", "run", "run-1", now, null));
    repository.insert(row("DraftPublished", "definition", "def-A", now, null));

    var result = repository.search("RunCompleted", null, null, null, null, 0, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).eventType()).isEqualTo("RunCompleted");
  }

  @Test
  void aggregateTypeAndIdFiltersNarrowTogether() {
    Instant now = Instant.now();
    repository.insert(row("RunStarted", "run", "run-1", now, null));
    repository.insert(row("RunStarted", "run", "run-2", now, null));
    repository.insert(row("DraftPublished", "definition", "def-A", now, null));

    var result = repository.search(null, "run", "run-2", null, null, 0, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).aggregateId()).isEqualTo("run-2");
  }

  @Test
  void correlationIdFilterNarrowsResults() {
    Instant now = Instant.now();
    repository.insert(row("RunStarted", "run", "run-1", now, "corr-abc"));
    repository.insert(row("RunStarted", "run", "run-2", now, null));
    repository.insert(row("RunStarted", "run", "run-3", now, "corr-xyz"));

    var result = repository.search(null, null, null, "corr-abc", null, 0, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).aggregateId()).isEqualTo("run-1");
    assertThat(result.items().get(0).correlationId()).isEqualTo("corr-abc");
  }

  @Test
  void sinceFilterIncludesRowsAtAndAfterInstant() {
    Instant now = Instant.now();
    Instant old = now.minus(2, java.time.temporal.ChronoUnit.HOURS);
    Instant fresh = now.plus(5, java.time.temporal.ChronoUnit.MINUTES);
    repository.insert(row("RunStarted", "run", "old", old, null));
    repository.insert(row("RunStarted", "run", "fresh", fresh, null));

    Instant since = now.minus(1, java.time.temporal.ChronoUnit.HOURS);
    var result = repository.search(null, null, null, null, since, 0, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items()).extracting(DslEventEntity::aggregateId)
            .containsExactly("fresh");
  }

  @Test
  void searchAppliesLimitAndOffset() {
    Instant now = Instant.now();
    for (int i = 0; i < 5; i++) {
      repository.insert(row("RunStarted", "run", "run-" + i,
              now.minus(5 - i, java.time.temporal.ChronoUnit.MINUTES), null));
    }

    var page = repository.search(null, null, null, null, null, 1, 2);

    assertThat(page.total()).isEqualTo(5);
    assertThat(page.items()).extracting(DslEventEntity::aggregateId)
            .containsExactly("run-3", "run-2");
  }

  @Test
  void emptyFiltersAndTableReturnsEmptyPageWithZeroTotal() {
    var result = repository.search(null, null, null, null, null, 0, 10);

    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void searchRejectsBadPagination() {
    assertThatThrownBy(() -> repository.search(null, null, null, null, null, -1, 10))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> repository.search(null, null, null, null, null, 0, 0))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void appendedPayloadJsonIsStoredVerbatim() {
    Instant now = Instant.now();
    String payload = "{\"nested\":{\"x\":42,\"y\":[1,2,3]}}";
    DslEventEntity row = new DslEventEntity(null, "ReloadFailed", "definition",
            "/dsl", "corr-7", payload, 1, now);
    repository.insert(row);

    var result = repository.search("ReloadFailed", null, null, null, null, 0, 10);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).payloadJson()).isEqualTo(payload);
    assertThat(result.items().get(0).schemaVersion()).isEqualTo(1);
  }

  private static DslEventEntity row(String eventType, String aggregateType, String aggregateId,
          Instant createdAt, String correlationId) {
    return new DslEventEntity(null, eventType, aggregateType, aggregateId, correlationId,
            "{\"k\":1}", 1, createdAt);
  }
}
