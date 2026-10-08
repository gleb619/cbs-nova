package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.AuditTestSupport.Harness;
import cbs.nova.starter.entity.DslAuditEntity;
import cbs.nova.starter.model.AuditChainVerification;
import cbs.nova.starter.model.DslAudit;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T880 — unit tests for the canonical hash computation and the end-to-end chain behavior on the
 * {@code dsl_audit} table. Runs against an in-memory H2 database (V1 + V4 migrations applied via
 * {@link AuditTestSupport}). Tamper and legacy-row injection use a raw {@link JdbcTemplate} so the
 * chain path under test ({@link DslAuditStore#append}) is bypassed.
 */
class AuditHashChainTest {

  private Harness harness;
  private JdbcTemplate jdbc;
  private AnnotationConfigApplicationContext ctx;

  @BeforeEach
  void setUp() {
    harness = AuditTestSupport.h2();
    ctx = AuditTestSupport.lastContext();
    DataSource ds = ctx.getBean(DataSource.class);
    jdbc = new JdbcTemplate(ds);
  }

  @Test
  void appendPopulatesPrevAndEntryHashOnEveryRow() {
    Instant now = Instant.now();
    harness.append(row(now, "op-1", "DEFINITION_PUBLISH", "LoanFlow"));
    harness.append(row(now.plus(1, ChronoUnit.MINUTES), "op-2", "DRAFT_WRITE", "LoanFlow"));
    harness.append(row(now.plus(2, ChronoUnit.MINUTES), "op-3", "RUN_RERUN", "LoanFlow"));

    var rows = harness.service().search(null, 0, 10).items();

    assertThat(rows).hasSize(3);
    // Newest first in the read API; the oldest row must have prev_hash == null.
    var oldest = rows.get(2);
    assertThat(oldest.prevHash()).isNull();
    assertThat(oldest.entryHash()).matches("[0-9a-f]{64}");

    var middle = rows.get(1);
    assertThat(middle.prevHash()).isEqualTo(oldest.entryHash());
    assertThat(middle.entryHash()).matches("[0-9a-f]{64}");

    var newest = rows.get(0);
    assertThat(newest.prevHash()).isEqualTo(middle.entryHash());
    assertThat(newest.entryHash()).matches("[0-9a-f]{64}");
  }

  @Test
  void verifyChainReturnsOkForCleanChain() {
    Instant now = Instant.now();
    harness.append(row(now, "op-1", "DEFINITION_PUBLISH", "LoanFlow"));
    harness.append(row(now.plus(1, ChronoUnit.MINUTES), "op-2", "DRAFT_WRITE", "LoanFlow"));

    AuditChainVerification verdict = harness.store().verifyChain();

    assertThat(verdict.ok()).isTrue();
    assertThat(verdict.checked()).isEqualTo(2L);
    assertThat(verdict.firstDivergenceId()).isNull();
    assertThat(verdict.firstDivergenceAt()).isNull();
    assertThat(verdict.reason()).isNull();
  }

  @Test
  void verifyChainOnEmptyTableReturnsOk() {
    AuditChainVerification verdict = harness.store().verifyChain();

    assertThat(verdict.ok()).isTrue();
    assertThat(verdict.checked()).isZero();
  }

  @Test
  void verifyChainDetectsTamperedDetail() {
    Instant now = Instant.now();
    harness.append(row(now, "op-1", "DEFINITION_PUBLISH", "LoanFlow"));
    harness.append(row(now.plus(1, ChronoUnit.MINUTES), "op-2", "DRAFT_WRITE", "LoanFlow"));
    harness.append(row(now.plus(2, ChronoUnit.MINUTES), "op-3", "RUN_RERUN", "LoanFlow"));

    var rows = harness.service().search(null, 0, 10).items();
    var middle = rows.get(1);
    // Tamper with the middle row's details_json by writing a new value through raw JDBC.
    // The stored entry_hash was computed for the original details, so the walker detects the
    // divergence when it recomputes over the new details.
    jdbc.update("UPDATE dsl_audit SET details_json = ? WHERE id = ?",
            "{\"n\":99}", middle.id());

    AuditChainVerification verdict = harness.store().verifyChain();

    assertThat(verdict.ok()).isFalse();
    assertThat(verdict.firstDivergenceId()).isEqualTo(middle.id());
    assertThat(verdict.reason())
            .contains("entry_hash does not match")
            .isNotBlank();
  }

  @Test
  void verifyChainDetectsBrokenPrevHashLink() {
    Instant now = Instant.now();
    harness.append(row(now, "op-1", "DEFINITION_PUBLISH", "LoanFlow"));
    harness.append(row(now.plus(1, ChronoUnit.MINUTES), "op-2", "DRAFT_WRITE", "LoanFlow"));

    var rows = harness.service().search(null, 0, 10).items();
    var newest = rows.get(0);
    // Replace the newest row's prev_hash with a bogus value; the walker should fail the link
    // check before even attempting to recompute the entry hash.
    jdbc.update("UPDATE dsl_audit SET prev_hash = ? WHERE id = ?",
            "0000000000000000000000000000000000000000000000000000000000000000", newest.id());

    AuditChainVerification verdict = harness.store().verifyChain();

    assertThat(verdict.ok()).isFalse();
    assertThat(verdict.firstDivergenceId()).isEqualTo(newest.id());
    assertThat(verdict.reason()).contains("prev_hash does not match");
  }

  @Test
  void verifyChainSkipsLegacyNullHashRows() {
    // Inject a pre-V4 row (entry_hash = null) directly via JDBC — the walker must skip it and
    // start the chain at the first post-migration row.
    Instant legacyAt = Instant.now().minus(10, ChronoUnit.MINUTES);
    jdbc.update(
            "INSERT INTO dsl_audit (occurred_at, actor, action, target, outcome, details_json) "
                    + "VALUES (?, ?, ?, ?, ?, ?)",
            java.sql.Timestamp.from(legacyAt), "legacy", "DEFINITION_PUBLISH", "LegacyFlow",
            "SUCCESS", "{\"v\":1}");
    harness.append(row(Instant.now(), "op-new", "DRAFT_WRITE", "LoanFlow"));

    AuditChainVerification verdict = harness.store().verifyChain();

    assertThat(verdict.ok()).isTrue();
    // Only the post-migration row is "checked"; the legacy row is skipped.
    assertThat(verdict.checked()).isEqualTo(1L);
  }

  @Test
  void verifyChainFlagsFirstPostMigrationRowWhenItLinksToLegacyTail() {
    // A pre-V4 row exists, then a post-V4 row is appended. The post-V4 row's prev_hash is the
    // most recent row's entry_hash at insert time, which is null (legacy has no entry_hash).
    // So the chain walker accepts the post-V4 row only if its prev_hash is also null. The
    // store inserts it with prev_hash = null, so this should pass.
    Instant legacyAt = Instant.now().minus(10, ChronoUnit.MINUTES);
    jdbc.update(
            "INSERT INTO dsl_audit (occurred_at, actor, action, target, outcome, details_json) "
                    + "VALUES (?, ?, ?, ?, ?, ?)",
            java.sql.Timestamp.from(legacyAt), "legacy", "DEFINITION_PUBLISH", "LegacyFlow",
            "SUCCESS", "{\"v\":1}");
    harness.append(row(Instant.now(), "op-new", "DRAFT_WRITE", "LoanFlow"));

    // Now corrupt the post-V4 row's prev_hash to something bogus — the walker should flag it.
    var rows = harness.service().search(null, 0, 10).items();
    var post = rows.get(0);
    jdbc.update("UPDATE dsl_audit SET prev_hash = ? WHERE id = ?",
            "deadbeef", post.id());

    AuditChainVerification verdict = harness.store().verifyChain();

    assertThat(verdict.ok()).isFalse();
    assertThat(verdict.firstDivergenceId()).isEqualTo(post.id());
    assertThat(verdict.reason()).contains("prev_hash does not match");
  }

  @Test
  void computeEntryHashIsStableForSameInput() {
    DslAuditEntity row = new DslAuditEntity(
            42L, Instant.parse("2026-10-09T10:00:00Z"),
            "op-1", "DEFINITION_PUBLISH", "LoanFlow", null, "SUCCESS", "{\"x\":1}",
            null, null);
    String prev = "abcdef0123456789";

    String first = AuditHashChain.computeEntryHash(prev, row);
    String second = AuditHashChain.computeEntryHash(prev, row);

    assertThat(first).isEqualTo(second).matches("[0-9a-f]{64}");
  }

  @Test
  void computeEntryHashChangesWhenAnyFieldChanges() {
    DslAuditEntity base = new DslAuditEntity(
            42L, Instant.parse("2026-10-09T10:00:00Z"),
            "op-1", "DEFINITION_PUBLISH", "LoanFlow", null, "SUCCESS", "{\"x\":1}",
            null, null);
    String baseHash = AuditHashChain.computeEntryHash("abc", base);

    assertThat(AuditHashChain.computeEntryHash("abc",
            withId(base, 43L))).isNotEqualTo(baseHash);
    assertThat(AuditHashChain.computeEntryHash("abc",
            withField(base, "op-2", base.action(), base.target(), base.outcome(),
                    base.detailsJson())))
            .isNotEqualTo(baseHash);
    assertThat(AuditHashChain.computeEntryHash("abc",
            withField(base, base.actor(), "DRAFT_WRITE", base.target(), base.outcome(),
                    base.detailsJson())))
            .isNotEqualTo(baseHash);
    assertThat(AuditHashChain.computeEntryHash("abc",
            withField(base, base.actor(), base.action(), "CreditFlow", base.outcome(),
                    base.detailsJson())))
            .isNotEqualTo(baseHash);
    assertThat(AuditHashChain.computeEntryHash("abc",
            withField(base, base.actor(), base.action(), base.target(), "FAILURE",
                    base.detailsJson())))
            .isNotEqualTo(baseHash);
    assertThat(AuditHashChain.computeEntryHash("abc",
            withField(base, base.actor(), base.action(), base.target(), base.outcome(),
                    "{\"x\":2}")))
            .isNotEqualTo(baseHash);
    assertThat(AuditHashChain.computeEntryHash("xyz", base)).isNotEqualTo(baseHash);
  }

  @Test
  void computeEntryHashRejectsNullId() {
    DslAuditEntity row = new DslAuditEntity(
            null, Instant.parse("2026-10-09T10:00:00Z"),
            "op-1", "DEFINITION_PUBLISH", "LoanFlow", null, "SUCCESS", "{\"x\":1}",
            null, null);
    assertThatThrownBy(() -> AuditHashChain.computeEntryHash("abc", row))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("row.id is required");
  }

  @Test
  void computeEntryHashRejectsNullRow() {
    assertThatThrownBy(() -> AuditHashChain.computeEntryHash("abc", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("row is required");
  }

  // --- helpers -----------------------------------------------------------------------------

  private static DslAudit row(Instant occurredAt, String actor, String action, String target) {
    return new DslAudit(null, occurredAt, actor, action, target, null, "SUCCESS",
            "{\"n\":1}", null, null);
  }

  private static DslAuditEntity withId(DslAuditEntity row, long id) {
    return new DslAuditEntity(id, row.occurredAt(), row.actor(), row.action(),
            row.target(), row.correlationId(), row.outcome(), row.detailsJson(),
            row.prevHash(), row.entryHash());
  }

  private static DslAuditEntity withField(DslAuditEntity row, String actor, String action,
          String target, String outcome, String detailsJson) {
    return new DslAuditEntity(row.id(), row.occurredAt(), actor, action, target,
            row.correlationId(), outcome, detailsJson, row.prevHash(), row.entryHash());
  }
}
