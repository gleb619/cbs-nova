package cbs.nova.dsl.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.SimpleContext;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class ContextFactoryTest {

  private static final int COLLISION_SAMPLE = 100;

  private final ContextFactory contextFactory = new ContextFactory();

  @Test
  void generateRunIdIsNonBlankAndPrefixedWithRun() {
    String runId = contextFactory.generateRunId();

    assertThat(runId)
            .isNotNull()
            .isNotBlank()
            .startsWith("run-");
  }

  @Test
  void generateRunIdSuffixParsesAsUuid() {
    String runId = contextFactory.generateRunId();

    String suffix = runId.substring("run-".length());

    assertThat(suffix).isNotBlank();
    // Must not throw — confirms suffix matches UUID textual format.
    UUID parsed = UUID.fromString(suffix);
    assertThat(parsed.toString()).isEqualTo(suffix);
  }

  @Test
  void generateRunIdReturnsDistinctValuesAcrossCalls() {
    Set<String> seen = new HashSet<>(COLLISION_SAMPLE * 2);

    for (int i = 0; i < COLLISION_SAMPLE; i++) {
      seen.add(contextFactory.generateRunId());
    }

    assertThat(seen).hasSize(COLLISION_SAMPLE);
  }

  @Test
  void ofBodyAndModeGeneratesRunIdAndEmptyMetadata() {
    SimpleContext<String> first = contextFactory.of("payload", ExecutionMode.RUN);
    SimpleContext<String> second = contextFactory.of("payload", ExecutionMode.RUN);

    assertThat(first.body()).isEqualTo("payload");
    assertThat(first.mode()).isEqualTo(ExecutionMode.RUN);
    assertThat(first.metadata()).isEmpty();
    assertThat(first.runId()).isNotBlank().startsWith("run-");
    assertThat(second.runId()).isNotBlank().isNotEqualTo(first.runId());
  }

  // --- of(body, mode, runId) ----------------------------------------------

  @Test
  void ofWithExplicitRunIdPreservesRunIdAndHasEmptyMetadata() {
    String supplied = "run-supplied-001";

    SimpleContext<String> ctx = contextFactory.of("payload", ExecutionMode.PREVIEW, supplied);

    assertThat(ctx.body()).isEqualTo("payload");
    assertThat(ctx.mode()).isEqualTo(ExecutionMode.PREVIEW);
    assertThat(ctx.runId()).isEqualTo(supplied);
    assertThat(ctx.metadata()).isEmpty();
  }

  @Test
  void ofWithMetadataAndRunIdPreservesBoth() {
    String suppliedRunId = "run-supplied-002";
    Map<String, Object> metadata = Map.of("traceId", "abc-123", "retry", 3);

    SimpleContext<String> ctx = contextFactory.of("payload", metadata, ExecutionMode.EXPLAIN,
            suppliedRunId);

    assertThat(ctx.body()).isEqualTo("payload");
    assertThat(ctx.mode()).isEqualTo(ExecutionMode.EXPLAIN);
    assertThat(ctx.runId()).isEqualTo(suppliedRunId);
    assertThat(ctx.metadata())
            .containsAllEntriesOf(metadata)
            .hasSize(metadata.size());
  }

  // --- ExecutionMode propagation ------------------------------------------

  @Test
  void executionModePropagatesThroughAllOverloads() {
    for (ExecutionMode mode : new ExecutionMode[]{ExecutionMode.RUN, ExecutionMode.EXPLAIN}) {
      SimpleContext<String> twoArg = contextFactory.of("payload", mode);
      SimpleContext<String> threeArg = contextFactory.of("payload", mode, "run-" + mode);
      SimpleContext<String> fourArg = contextFactory.of("payload", Map.of("k", "v"), mode,
              "run-" + mode);

      assertThat(twoArg.mode()).as("of(body,mode) with %s", mode).isEqualTo(mode);
      assertThat(threeArg.mode()).as("of(body,mode,runId) with %s", mode).isEqualTo(mode);
      assertThat(fourArg.mode()).as("of(body,metadata,mode,runId) with %s", mode).isEqualTo(mode);
    }
  }
}
