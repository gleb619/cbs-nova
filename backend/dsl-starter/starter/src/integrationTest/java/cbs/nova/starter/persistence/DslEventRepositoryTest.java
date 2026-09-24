package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.entity.DslEventEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import cbs.nova.starter.persistence.ExtendedSelectQueryExecutor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Live Postgres tests for {@link DslEventRepository} against the V7 migration. The Postgres column
 * is {@code JSONB} while H2 stores payloads as {@code TEXT}, so the {@code JSONB} contract is
 * pinned using the same Postgres migration the runtime uses. JSONB normalizes formatting, so
 * payload assertions compare the parsed value rather than the raw string.
 */
@Deprecated
@Testcontainers
class DslEventRepositoryTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  private static PGSimpleDataSource dataSource;
  private NamedParameterJdbcTemplate jdbcTemplate;
  private ExtendedSelectQueryExecutor dslQueries;
  private DslEventRepository repository;

  @BeforeAll
  static void setUpDatabase() throws Exception {
    dataSource = new PGSimpleDataSource();
    dataSource.setUrl(postgres.getJdbcUrl());
    dataSource.setUser(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());

    ScriptUtils.executeSqlScript(dataSource.getConnection(),
            new ClassPathResource("db/migration/postgres/V1__init.sql"));
  }

  @BeforeEach
  void setUp() {
    jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    jdbcTemplate.getJdbcTemplate().execute("TRUNCATE dsl_events RESTART IDENTITY");
    dslQueries = new ExtendedSelectQueryExecutor(jdbcTemplate);
    repository = new DslEventRepository(jdbcTemplate, dslQueries);
  }

  @Test
  void insertThenSearchRoundTripsNewestFirst() {
    Instant now = Instant.now();
    repository.insert(row("RunStarted", "run", "run-1",
            now.minus(2, ChronoUnit.MINUTES), null));
    repository.insert(row("RunCompleted", "run", "run-1",
            now.minus(1, ChronoUnit.MINUTES), null));
    repository.insert(row("DraftPublished", "definition", "def-A", now, null));

    var result = repository.search(null, null, null, null, null, 0, 10);

    assertThat(result.total()).isEqualTo(3);
    assertThat(result.items()).extracting(DslEventEntity::aggregateId)
            .containsExactly("def-A", "run-1", "run-1");
    assertThat(result.items()).allSatisfy(r -> {
      assertThat(r.id()).isNotNull();
      assertThat(r.schemaVersion()).isEqualTo(1);
      assertThat(r.createdAt()).isNotNull();
      assertPayloadIsK1(r.payloadJson());
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
    Instant old = now.minus(2, ChronoUnit.HOURS);
    Instant fresh = now.plus(5, ChronoUnit.MINUTES);
    repository.insert(row("RunStarted", "run", "old", old, null));
    repository.insert(row("RunStarted", "run", "fresh", fresh, null));

    Instant since = now.minus(1, ChronoUnit.HOURS);
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
              now.minus(5 - i, ChronoUnit.MINUTES), null));
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
  void appendedPayloadJsonIsStoredVerbatim() throws Exception {
    Instant now = Instant.now();
    String payload = "{\"nested\":{\"x\":42,\"y\":[1,2,3]}}";
    DslEventEntity row = new DslEventEntity(null, "ReloadFailed", "definition",
            "/dsl", "corr-7", payload, 1, now);
    repository.insert(row);

    var result = repository.search("ReloadFailed", null, null, null, null, 0, 10);

    assertThat(result.items()).hasSize(1);
    JsonNode actual = MAPPER.readTree(result.items().get(0).payloadJson());
    JsonNode expected = MAPPER.readTree(payload);
    assertThat(actual).isEqualTo(expected);
    assertThat(result.items().get(0).schemaVersion()).isEqualTo(1);
  }

  private static DslEventEntity row(String eventType, String aggregateType, String aggregateId,
          Instant createdAt, String correlationId) {
    return new DslEventEntity(null, eventType, aggregateType, aggregateId, correlationId,
            "{\"k\":1}", 1, createdAt);
  }

  private static JsonNode node(String json) throws Exception {
    return MAPPER.readTree(json);
  }

  private static void assertPayloadIsK1(String payloadJson) throws Exception {
    assertThat(node(payloadJson)).isEqualTo(node("{\"k\":1}"));
  }
}
