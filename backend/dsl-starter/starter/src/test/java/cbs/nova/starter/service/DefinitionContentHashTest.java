package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefinitionContentHashTest {

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void nullAndBlankProcessNameReturnNull() {
    assertThat(DefinitionContentHash.of(null)).isNull();
    assertThat(DefinitionContentHash.of("")).isNull();
    assertThat(DefinitionContentHash.of("   ")).isNull();
  }

  @Test
  void unresolvableNameReturnsNull() {
    assertThat(DefinitionContentHash.of("missing-process")).isNull();
  }

  @Test
  void returnsNullWhenNothingPublishedForResolvableDefinition() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("UnpublishedProcess")
                    .execute(ctx -> Result.success("ok"))
                    .build());

    assertThat(DefinitionContentHash.of("UnpublishedProcess")).isNull();
  }

  @Test
  void publishThenLookupReturnsDeterministic64CharHexSha256() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("PublishedProcess")
                    .execute(ctx -> Result.success("ok"))
                    .build());
    Map<String, String> sources = Map.of("PublishedProcess.java", "class PublishedProcess {}");

    DefinitionContentHash.publish(sources, GlobalManager.globalManager());

    String first = DefinitionContentHash.of("PublishedProcess");
    String second = DefinitionContentHash.of("PublishedProcess");

    assertThat(first)
            .isNotNull()
            .isEqualTo(second)
            .matches("[0-9a-f]{64}");
  }

  @Test
  void publishCoversTransactionsToo() {
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction("PublishedTransaction")
                    .execute(ctx -> Result.success("ok"))
                    .build());

    DefinitionContentHash.publish(
            Map.of("PublishedTransaction.java", "tx"), GlobalManager.globalManager());

    assertThat(DefinitionContentHash.of("PublishedTransaction"))
            .isNotNull()
            .matches("[0-9a-f]{64}");
  }

  @Test
  void differentSourceSnapshotsYieldDifferentHashes() {
    GlobalManager gm = GlobalManager.globalManager();
    gm.registerProcess(Dsl.process("SnapshotA").execute(ctx -> Result.success("a")).build());
    gm.registerProcess(Dsl.process("SnapshotB").execute(ctx -> Result.success("b")).build());

    DefinitionContentHash.publish(Map.of("X.java", "v1"), gm);
    String hashV1 = DefinitionContentHash.of("SnapshotA");

    DefinitionContentHash.publish(Map.of("X.java", "v2"), gm);
    String hashV2 = DefinitionContentHash.of("SnapshotA");

    assertThat(hashV1).isNotNull().isNotEqualTo(hashV2);
    assertThat(DefinitionContentHash.of("SnapshotB")).isEqualTo(hashV2);
  }

  @Test
  void sameNameDifferentVersionsAreKeyedSeparately() {
    GlobalManager gm = GlobalManager.globalManager();
    gm.registerProcess(Dsl.process("Versioned").version("v1")
            .execute(ctx -> Result.success("v1")).build());
    DefinitionContentHash.publish(Map.of("V.java", "v1"), gm);
    String hashV1 = DefinitionContentHash.of("Versioned");

    gm.registerProcess(Dsl.process("Versioned").version("v2")
            .execute(ctx -> Result.success("v2")).build());
    DefinitionContentHash.publish(Map.of("V.java", "v2"), gm);

    assertThat(DefinitionContentHash.of("Versioned")).isNotEqualTo(hashV1);
  }

  @Test
  void publishNeverThrowsOnBadInput() {
    assertThatCode(() -> DefinitionContentHash.publish(null, null)).doesNotThrowAnyException();
    assertThatCode(() -> DefinitionContentHash.publish(Map.of(), GlobalManager.globalManager()))
            .doesNotThrowAnyException();
  }
}
