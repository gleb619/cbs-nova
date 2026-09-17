package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.process.ProcessDslObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunDefinitionHashTest {

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
    assertThat(RunDefinitionHash.of(null)).isNull();
    assertThat(RunDefinitionHash.of("")).isNull();
    assertThat(RunDefinitionHash.of("   ")).isNull();
  }

  @Test
  void resolvableProcessReturnsDeterministic64CharHexSha256() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("HashedProcess")
                    .execute(ctx -> Result.success("ok"))
                    .build());

    String first = RunDefinitionHash.of("HashedProcess");
    String second = RunDefinitionHash.of("HashedProcess");

    assertThat(first)
            .isNotNull()
            .isEqualTo(second)
            .matches("[0-9a-f]{64}");
  }

  @Test
  void resolvableTransactionReturnsHashWhenProcessMisses() {
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction("HashedTransaction")
                    .execute(ctx -> Result.success("ok"))
                    .build());

    String hash = RunDefinitionHash.of("HashedTransaction");

    assertThat(hash).isNotNull().matches("[0-9a-f]{64}");
  }

  @Test
  void unresolvableNameReturnsNull() {
    assertThat(RunDefinitionHash.of("missing-process")).isNull();
  }

  @Test
  void descriptorIdentityCollisionHashesDifferentLogicToSameValue() {
    ProcessDslObject source = Dsl.process("SameIdentity")
            .taskQueue("shared-queue")
            .version("v2")
            .execute(ctx -> Result.success("a"))
            .build();

    ProcessDslObject first = ProcessDslObject.builder()
            .name("first")
            .description(source.description())
            .taskQueue(source.taskQueue())
            .version(source.version())
            .inputType(source.inputType())
            .outputType(source.outputType())
            .parameters(source.parameters())
            .executeLogic(ctx -> Result.success("a"))
            .compensationLogic(source.compensationLogic())
            .previewLogic(source.previewLogic())
            .explainLogic(source.explainLogic())
            .descriptor(source.descriptor())
            .build();

    ProcessDslObject second = ProcessDslObject.builder()
            .name("second")
            .description(source.description())
            .taskQueue(source.taskQueue())
            .version(source.version())
            .inputType(source.inputType())
            .outputType(source.outputType())
            .parameters(source.parameters())
            .executeLogic(ctx -> Result.success("b"))
            .compensationLogic(source.compensationLogic())
            .previewLogic(source.previewLogic())
            .explainLogic(source.explainLogic())
            .descriptor(source.descriptor())
            .build();

    GlobalManager.globalManager().registerProcess(first);
    GlobalManager.globalManager().registerProcess(second);

    assertThat(RunDefinitionHash.of("first")).isEqualTo(RunDefinitionHash.of("second"));
  }
}
