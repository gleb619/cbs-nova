package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunSearchResult;
import cbs.nova.starter.converter.DslRunMapperImpl;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Live H2 spec for T879: {@code cbs.nova.persistence.run.encryption.enabled=true} wires
 * {@link AesFieldEncryptor} into {@link JdbcDslRunRepository} via {@link DslRunEncryption}. The
 * cipher is exercised in isolation by {@code AesFieldEncryptorTest}; this spec proves the
 * repository stores ciphertext in {@code input_json / output_json / context_json} and the
 * {@code error_message} column on the {@code updateFinished*} paths, and that every read path
 * (save, findByRunId, findByProcessName, search, updateFinished, updateFinishedIfRunning) returns
 * decrypted plaintext for the fields {@link DslRunEncryption} covers on the entity.
 */
class EncryptedRunRepositoryTest {

  private static final String ENCRYPTION_KEY = "t879-fixed-test-key-32-bytes-min";
  private static final String TABLE = "dsl_runs";
  private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");
  private static final String INPUT_JSON = "{\"customer\":\"alice\",\"amount\":100}";
  private static final String OUTPUT_JSON = "{\"status\":\"approved\",\"token\":\"xyz\"}";
  private static final String CONTEXT_JSON = "{\"trace\":\"abc\",\"step\":1}";
  private static final String ERROR_MESSAGE = "downstream timeout";
  private static final String CIPHERTEXT_PATTERN = "[A-Za-z0-9+/=]+";

  private static JdbcTemplate jdbc;
  private static JdbcDslRunRepository encryptedRepository;
  private static JdbcDslRunRepository noopRepository;

  @BeforeAll
  static void setUp() throws Exception {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:t879-enc-" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
    ds.setUser("sa");
    ScriptUtils.executeSqlScript(ds.getConnection(),
            new ClassPathResource("db/migration/h2/V1__init.sql"));
    DataSource dataSource = ds;
    jdbc = new JdbcTemplate(dataSource);
    NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);

    var ctx = new AnnotationConfigApplicationContext();
    ctx.registerBean("dataSource", DataSource.class, () -> dataSource);
    ctx.registerBean("transactionManager", PlatformTransactionManager.class,
            () -> new DataSourceTransactionManager(dataSource));
    ctx.registerBean(NamedParameterJdbcOperations.class, () -> namedJdbc);
    ctx.register(RepositoryConfig.class);
    ctx.refresh();
    DslRunJdbcRepository jdbcRepository = ctx.getBean(DslRunJdbcRepository.class);
    DslRunMapperImpl mapper = new DslRunMapperImpl();
    ExtendedSelectQueryExecutor dslQueries = new ExtendedSelectQueryExecutor(namedJdbc);
    encryptedRepository = new JdbcDslRunRepository(dslQueries, jdbcRepository, mapper,
            new DslRunEncryption(new AesFieldEncryptor(ENCRYPTION_KEY)), TABLE);
    noopRepository = new JdbcDslRunRepository(dslQueries, jdbcRepository, mapper,
            new DslRunEncryption(new NoOpFieldEncryptor()), TABLE);
  }

  @BeforeEach
  void truncate() {
    jdbc.execute("DELETE FROM dsl_runs");
  }

  @Test
  void savePersistsInputOutputAndContextAsCiphertextAtRest() {
    encryptedRepository.save(run("run-1", "RUNNING", INPUT_JSON, null, null, CONTEXT_JSON));

    String inputRest = rawColumn("run-1", "input_json");
    String contextRest = rawColumn("run-1", "context_json");
    String outputRest = rawColumn("run-1", "output_json");
    String errorRest = rawColumn("run-1", "error_message");

    assertThat(inputRest)
            .as("input_json must be encrypted at rest, not stored as plaintext")
            .isNotEqualTo(INPUT_JSON)
            .doesNotContain("alice")
            .doesNotContain("\"amount\"")
            .matches(CIPHERTEXT_PATTERN);
    assertThat(contextRest)
            .isNotEqualTo(CONTEXT_JSON)
            .doesNotContain("\"trace\"")
            .matches(CIPHERTEXT_PATTERN);
    assertThat(outputRest)
            .as("null output_json stays null, no encryption of null")
            .isNull();
    assertThat(errorRest).isNull();
  }

  @Test
  void findByRunIdReturnsDecryptedPlaintextForInputOutputAndContext() {
    encryptedRepository.save(run("run-1", "RUNNING", INPUT_JSON, null, null, CONTEXT_JSON));

    DslRun reloaded = encryptedRepository.findByRunId("run-1").orElseThrow();

    assertThat(reloaded.input()).isEqualTo(INPUT_JSON);
    assertThat(reloaded.contextJson()).isEqualTo(CONTEXT_JSON);
    assertThat(reloaded.output()).isNull();
    assertThat(reloaded.error()).isNull();
    assertThat(reloaded.runId()).isEqualTo("run-1");
    assertThat(reloaded.processName()).isEqualTo("Payments");
  }

  @Test
  void findByProcessNameDecryptsEveryReturnedRow() {
    encryptedRepository.save(run("p-1", "RUNNING", INPUT_JSON, null, null, CONTEXT_JSON));
    encryptedRepository.save(run("p-2", "RUNNING", INPUT_JSON, null, null, CONTEXT_JSON));
    encryptedRepository.save(run("other", "RUNNING", "Other", INPUT_JSON, null, null, CONTEXT_JSON));

    List<DslRun> payments = encryptedRepository.findByProcessName("Payments");

    assertThat(payments).extracting(DslRun::runId).containsExactlyInAnyOrder("p-1", "p-2");
    assertThat(payments).allSatisfy(r -> {
      assertThat(r.input()).isEqualTo(INPUT_JSON);
      assertThat(r.contextJson()).isEqualTo(CONTEXT_JSON);
    });
  }

  @Test
  void searchDecryptsEveryReturnedItem() {
    encryptedRepository.save(run("s-1", "RUNNING", INPUT_JSON, null, null, CONTEXT_JSON));
    encryptedRepository.save(run("s-2", "RUNNING", INPUT_JSON, null, null, CONTEXT_JSON));

    DslRunSearchResult result = encryptedRepository.search("Payments", null, null, null, 0, 10);

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).extracting(DslRun::runId)
            .containsExactlyInAnyOrder("s-1", "s-2");
    assertThat(result.items()).allSatisfy(r -> {
      assertThat(r.input()).isEqualTo(INPUT_JSON);
      assertThat(r.contextJson()).isEqualTo(CONTEXT_JSON);
    });
  }

  @Test
  void updateFinishedEncryptsOutputAndContextAtRestAndDecryptsOnRead() {
    encryptedRepository.save(run("u-1", "RUNNING", INPUT_JSON, null, null, CONTEXT_JSON));

    DslRun finished = encryptedRepository.updateFinished("u-1", "COMPLETED", OUTPUT_JSON, null,
            NOW.plusSeconds(5), CONTEXT_JSON);

    String outputRest = rawColumn("u-1", "output_json");
    String contextRest = rawColumn("u-1", "context_json");
    assertThat(outputRest)
            .as("output_json must be encrypted at rest after updateFinished")
            .isNotEqualTo(OUTPUT_JSON)
            .doesNotContain("approved")
            .matches(CIPHERTEXT_PATTERN);
    assertThat(contextRest)
            .isNotEqualTo(CONTEXT_JSON)
            .doesNotContain("\"trace\"")
            .matches(CIPHERTEXT_PATTERN);

    assertThat(finished.status()).isEqualTo("COMPLETED");
    assertThat(finished.output()).isEqualTo(OUTPUT_JSON);
    assertThat(finished.contextJson()).isEqualTo(CONTEXT_JSON);

    DslRun reloaded = encryptedRepository.findByRunId("u-1").orElseThrow();
    assertThat(reloaded.output()).isEqualTo(OUTPUT_JSON);
    assertThat(reloaded.contextJson()).isEqualTo(CONTEXT_JSON);
    assertThat(reloaded.status()).isEqualTo("COMPLETED");
  }

  @Test
  void updateFinishedEncryptsErrorColumnAtRest() {
    encryptedRepository.save(run("u-err", "RUNNING", INPUT_JSON, null, null, CONTEXT_JSON));

    encryptedRepository.updateFinished("u-err", "FAILED", null, ERROR_MESSAGE,
            NOW.plusSeconds(5), CONTEXT_JSON);

    String errorRest = rawColumn("u-err", "error_message");
    assertThat(errorRest)
            .as("error_message must be encrypted at rest after updateFinished")
            .isNotEqualTo(ERROR_MESSAGE)
            .doesNotContain("downstream")
            .doesNotContain("timeout")
            .matches(CIPHERTEXT_PATTERN);
  }

  @Test
  void updateFinishedIfRunningEncryptsOutputAndContextAndOnlyAffectsRunningRows() {
    encryptedRepository.save(run("ifr-1", "RUNNING", INPUT_JSON, null, null, CONTEXT_JSON));
    encryptedRepository.save(run("ifr-2", "RUNNING", INPUT_JSON, null, null, CONTEXT_JSON));
    encryptedRepository.save(run("ifr-3", "COMPLETED", INPUT_JSON, null, null, CONTEXT_JSON));

    int updated = encryptedRepository.updateFinishedIfRunning("ifr-1", "COMPLETED",
            OUTPUT_JSON, null, NOW.plusSeconds(5), CONTEXT_JSON);

    assertThat(updated).isEqualTo(1);
    String outputRest = rawColumn("ifr-1", "output_json");
    String contextRest = rawColumn("ifr-1", "context_json");
    assertThat(outputRest)
            .isNotEqualTo(OUTPUT_JSON)
            .doesNotContain("approved")
            .matches(CIPHERTEXT_PATTERN);
    assertThat(contextRest)
            .isNotEqualTo(CONTEXT_JSON)
            .matches(CIPHERTEXT_PATTERN);

    DslRun reloaded = encryptedRepository.findByRunId("ifr-1").orElseThrow();
    assertThat(reloaded.status()).isEqualTo("COMPLETED");
    assertThat(reloaded.output()).isEqualTo(OUTPUT_JSON);
    assertThat(reloaded.contextJson()).isEqualTo(CONTEXT_JSON);

    DslRun untouched = encryptedRepository.findByRunId("ifr-3").orElseThrow();
    assertThat(untouched.status()).isEqualTo("COMPLETED");
  }

  @Test
  void nullFieldsRemainNullAtRestAndOnRead() {
    encryptedRepository.save(run("run-null", "RUNNING", null, null, null, null));

    assertThat(rawColumn("run-null", "input_json")).isNull();
    assertThat(rawColumn("run-null", "output_json")).isNull();
    assertThat(rawColumn("run-null", "context_json")).isNull();
    assertThat(rawColumn("run-null", "error_message")).isNull();

    DslRun reloaded = encryptedRepository.findByRunId("run-null").orElseThrow();
    assertThat(reloaded.input()).isNull();
    assertThat(reloaded.output()).isNull();
    assertThat(reloaded.contextJson()).isNull();
    assertThat(reloaded.error()).isNull();
  }

  @Test
  void noOpEncryptorStoresPlaintextInAllPayloadColumns() {
    noopRepository.save(run("plain-1", "RUNNING", INPUT_JSON, null, null, CONTEXT_JSON));

    assertThat(rawColumn("plain-1", "input_json")).isEqualTo(INPUT_JSON);
    assertThat(rawColumn("plain-1", "context_json")).isEqualTo(CONTEXT_JSON);

    noopRepository.updateFinished("plain-1", "FAILED", OUTPUT_JSON, ERROR_MESSAGE,
            NOW.plusSeconds(5), CONTEXT_JSON);

    assertThat(rawColumn("plain-1", "output_json")).isEqualTo(OUTPUT_JSON);
    assertThat(rawColumn("plain-1", "error_message")).isEqualTo(ERROR_MESSAGE);
    assertThat(rawColumn("plain-1", "context_json")).isEqualTo(CONTEXT_JSON);

    DslRun reloaded = noopRepository.findByRunId("plain-1").orElseThrow();
    assertThat(reloaded.input()).isEqualTo(INPUT_JSON);
    assertThat(reloaded.output()).isEqualTo(OUTPUT_JSON);
    assertThat(reloaded.error()).isEqualTo(ERROR_MESSAGE);
    assertThat(reloaded.contextJson()).isEqualTo(CONTEXT_JSON);
  }

  private String rawColumn(String runId, String column) {
    return jdbc.queryForObject("SELECT " + column + " FROM " + TABLE + " WHERE run_id = ?",
            String.class, runId);
  }

  private static DslRun run(String runId, String status, String input, String output, String error,
          String context) {
    return run(runId, "Payments", status, input, output, error, context);
  }

  private static DslRun run(String runId, String processName, String status, String input,
          String output, String error, String context) {
    return DslRun.builder()
            .runId(runId)
            .processName(processName)
            .status(status)
            .startedAt(NOW)
            .executionMode("RUN")
            .input(input)
            .output(output)
            .error(error)
            .contextJson(context)
            .build();
  }
  @Configuration
  @EnableJdbcRepositories(basePackages = "cbs.nova.starter.persistence",
          includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                  classes = DslRunJdbcRepository.class))
  static class RepositoryConfig extends AbstractJdbcConfiguration {
  }
}