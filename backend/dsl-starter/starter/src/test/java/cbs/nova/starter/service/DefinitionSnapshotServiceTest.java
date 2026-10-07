package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.starter.model.DefinitionSnapshotDto;
import cbs.nova.starter.model.DefinitionVersionDto;
import cbs.nova.starter.persistence.CrudRepositories;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class DefinitionSnapshotServiceTest {

  private DefinitionSnapshotService service;

  @BeforeEach
  void setUp() throws Exception {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:snapshots-" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
            + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE");
    dataSource.setUser("sa");
    var connection = dataSource.getConnection();
    ScriptUtils.executeSqlScript(connection,
            new ClassPathResource("db/migration/h2/V1__init.sql"));
    ScriptUtils.executeSqlScript(connection,
            new ClassPathResource("db/migration/h2/V3__dsl_definition_snapshots.sql"));
    connection.close();
    service = new DefinitionSnapshotService(
            CrudRepositories.over(dataSource).dslDefinitionSnapshots());
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void captureStoresOneRowPerPublishedDefinition() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("LoanDsl").execute(ctx -> Result.success("ok")).build());

    service.capture(Map.of("LoanDsl.java", "class LoanDsl {}"),
            GlobalManager.globalManager(), "reload");

    List<DefinitionVersionDto> versions = service.versions("LoanDsl");
    assertThat(versions).hasSize(1);
    assertThat(versions.get(0).contentHash()).matches("[0-9a-f]{64}");
    assertThat(versions.get(0).version()).isNotBlank();
    assertThat(versions.get(0).capturedAt()).isNotBlank();
    assertThat(versions.get(0).capturedBy()).isEqualTo("reload");
  }

  @Test
  void unchangedHashCaptureIsIdempotent() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("LoanDsl").execute(ctx -> Result.success("ok")).build());
    Map<String, String> sources = Map.of("LoanDsl.java", "class LoanDsl {}");

    service.capture(sources, GlobalManager.globalManager(), "reload");
    service.capture(sources, GlobalManager.globalManager(), "publish");

    assertThat(service.versions("LoanDsl")).hasSize(1);
  }

  @Test
  void changedSourceAppendsNewVersionListedNewestFirst() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("LoanDsl").execute(ctx -> Result.success("ok")).build());

    service.capture(Map.of("LoanDsl.java", "v1"), GlobalManager.globalManager(), "reload");
    service.capture(Map.of("LoanDsl.java", "v2"), GlobalManager.globalManager(), "publish");

    List<DefinitionVersionDto> versions = service.versions("LoanDsl");
    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).capturedBy()).isEqualTo("publish");
    assertThat(versions.get(1).capturedBy()).isEqualTo("reload");
    assertThat(versions.get(0).contentHash()).isNotEqualTo(versions.get(1).contentHash());
  }

  @Test
  void versionsOfUnknownDefinitionAreEmpty() {
    assertThat(service.versions("missing")).isEmpty();
  }

  @Test
  void findReturnsEmptyForMissingHash() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("LoanDsl").execute(ctx -> Result.success("ok")).build());
    service.capture(Map.of("LoanDsl.java", "v1"), GlobalManager.globalManager(), "reload");

    assertThat(service.find("LoanDsl", "0".repeat(64))).isEmpty();
    assertThat(service.find("missing", "0".repeat(64))).isEmpty();
  }

  @Test
  void firstSnapshotHasNoDiff() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("LoanDsl").execute(ctx -> Result.success("ok")).build());
    service.capture(Map.of("LoanDsl.java", "v1"), GlobalManager.globalManager(), "reload");
    String hash = service.versions("LoanDsl").get(0).contentHash();

    Optional<DefinitionSnapshotDto> snapshot = service.find("LoanDsl", hash);

    assertThat(snapshot).isPresent();
    assertThat(snapshot.get().sourceText()).contains("v1");
    assertThat(snapshot.get().diff()).isNull();
  }

  @Test
  void secondSnapshotCarriesUnifiedDiffVsPrevious() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("LoanDsl").execute(ctx -> Result.success("ok")).build());
    service.capture(Map.of("LoanDsl.java", "before"), GlobalManager.globalManager(), "reload");
    service.capture(Map.of("LoanDsl.java", "after"), GlobalManager.globalManager(), "publish");

    String newestHash = service.versions("LoanDsl").get(0).contentHash();
    Optional<DefinitionSnapshotDto> snapshot = service.find("LoanDsl", newestHash);

    assertThat(snapshot).isPresent();
    assertThat(snapshot.get().diff())
            .contains("--- a/previous")
            .contains("-before")
            .contains("+after");
  }

  @Test
  void captureNeverThrowsOnBadInput() {
    assertThatCode(() -> service.capture(null, null, "reload")).doesNotThrowAnyException();
    assertThatCode(() -> service.capture(Map.of(), GlobalManager.globalManager(), "reload"))
            .doesNotThrowAnyException();
  }
}
