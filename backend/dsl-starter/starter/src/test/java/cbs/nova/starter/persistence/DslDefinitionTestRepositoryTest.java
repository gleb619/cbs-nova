package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.WebhookTestApplication;
import cbs.nova.starter.entity.DslDefinitionTestEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = WebhookTestApplication.class)
@Sql(scripts = {"classpath:db/migration/h2/V5__dsl_definition_tests.sql",
    "classpath:sql/truncate-dsl-definition-tests.sql"})
@TestPropertySource(properties = {
    "csb.dsl.worker.enabled=false"
})
class DslDefinitionTestRepositoryTest {

  @Autowired
  private DslDefinitionTestRepository repository;

  @Test
  void insertThenListRoundTripsRowsOrderedByCaseName() {
    Instant now = Instant.now();
    repository.insert(row("def-A", "zeta", now));
    repository.insert(row("def-A", "alpha", now));
    repository.insert(row("def-A", "mid", now));

    List<DslDefinitionTestEntity> rows = repository.listForDefinition("def-A");

    assertThat(rows).extracting(DslDefinitionTestEntity::caseName)
            .containsExactly("alpha", "mid", "zeta");
    assertThat(rows).allSatisfy(r -> {
      assertThat(r.id()).isNotNull();
      assertThat(r.definitionName()).isEqualTo("def-A");
      assertThat(r.inputJson()).isEqualTo("{\"x\":1}");
      assertThat(r.expectedOutputJson()).isEqualTo("{\"y\":2}");
    });
  }

  @Test
  void definitionsAreScopedIndependently() {
    Instant now = Instant.now();
    repository.insert(row("def-A", "case-1", now));
    repository.insert(row("def-B", "case-1", now));

    assertThat(repository.listForDefinition("def-A")).hasSize(1);
    assertThat(repository.listForDefinition("def-B")).hasSize(1);
    assertThat(repository.countByDefinition("def-A")).isEqualTo(1);
    assertThat(repository.countByDefinition("def-B")).isEqualTo(1);
  }

  @Test
  void uniqueKeyOnDefinitionAndCaseNameEnforced() {
    Instant now = Instant.now();
    repository.insert(row("def-A", "shared", now));

    assertThatThrownBy(() -> repository.insert(row("def-A", "shared", now)))
            .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void replaceAllAtomicallySwapsCaseSet() {
    Instant now = Instant.now();
    repository.insert(row("def-A", "old-1", now));
    repository.insert(row("def-A", "old-2", now));
    repository.insert(row("def-A", "kept", now));

    List<DslDefinitionTestEntity> replacement = List.of(
            new DslDefinitionTestEntity(null, "def-A", "fresh-1",
                    "{\"a\":1}", "{\"b\":2}", now, now),
            new DslDefinitionTestEntity(null, "def-A", "fresh-2",
                    "{\"c\":3}", "{\"d\":4}", now, now));

    repository.replaceAll("def-A", replacement);

    assertThat(repository.listForDefinition("def-A"))
            .extracting(DslDefinitionTestEntity::caseName)
            .containsExactly("fresh-1", "fresh-2");
    assertThat(repository.countByDefinition("def-A")).isEqualTo(2);
  }

  @Test
  void replaceAllWithEmptyListClearsAllRowsForDefinition() {
    Instant now = Instant.now();
    repository.insert(row("def-A", "case-1", now));
    repository.insert(row("def-A", "case-2", now));

    repository.replaceAll("def-A", List.of());

    assertThat(repository.listForDefinition("def-A")).isEmpty();
    assertThat(repository.countByDefinition("def-A")).isEqualTo(0);
  }

  @Test
  void deleteForDefinitionRemovesAllRows() {
    Instant now = Instant.now();
    repository.insert(row("def-A", "case-1", now));
    repository.insert(row("def-A", "case-2", now));

    repository.deleteForDefinition("def-A");

    assertThat(repository.listForDefinition("def-A")).isEmpty();
  }

  private static DslDefinitionTestEntity row(String definition, String caseName, Instant now) {
    return new DslDefinitionTestEntity(null, definition, caseName,
            "{\"x\":1}", "{\"y\":2}", now, now);
  }
}
