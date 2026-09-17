package cbs.nova.starter.core.stage;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.starter.model.PreviewModels.PreviewCacheKey;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreviewCacheKeyBuilderTest {

  private final PreviewCacheKeyBuilder builder = new PreviewCacheKeyBuilder();

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void sameNameAndBodyProduceEqualKeys() {
    Context<?> ctx = SimpleContext.builder("payload").mode(ExecutionMode.PREVIEW).runId("run-1")
            .build();
    String name = "unregistered-name-" + System.nanoTime();

    PreviewCacheKey first = builder.build(name, ctx);
    PreviewCacheKey second = builder.build(name, ctx);

    assertThat(first).isEqualTo(second);
    assertThat(first.processName()).isEqualTo(name);
    assertThat(first.dslDescriptorHash()).isEqualTo(second.dslDescriptorHash());
    assertThat(first.inputHash()).isEqualTo(second.inputHash());
  }

  @Test
  void differentBodiesProduceDifferentInputHashes() {
    String name = "unregistered-name-" + System.nanoTime();
    Context<?> ctxA = SimpleContext.builder("alpha").mode(ExecutionMode.PREVIEW).runId("run-a")
            .build();
    Context<?> ctxB = SimpleContext.builder("beta").mode(ExecutionMode.PREVIEW).runId("run-b")
            .build();

    PreviewCacheKey keyA = builder.build(name, ctxA);
    PreviewCacheKey keyB = builder.build(name, ctxB);

    assertThat(keyA.inputHash()).isNotEqualTo(keyB.inputHash());
    assertThat(keyA.processName()).isEqualTo(keyB.processName());
  }

  @Test
  void nameFlowsThroughUnchanged() {
    String name = "DistinctProcessName-" + System.nanoTime();
    Context<?> ctx = SimpleContext.builder("payload").mode(ExecutionMode.PREVIEW).runId("run-1")
            .build();

    PreviewCacheKey key = builder.build(name, ctx);

    assertThat(key.processName()).isEqualTo(name);
  }

  @Test
  void nullBodyStillHashesDeterministically() {
    String name = "unregistered-name-" + System.nanoTime();
    Context<Object> nullCtxA = SimpleContext.builder(null).mode(ExecutionMode.PREVIEW)
            .runId("run-a").build();
    Context<Object> nullCtxB = SimpleContext.builder(null).mode(ExecutionMode.PREVIEW)
            .runId("run-b").build();

    PreviewCacheKey first = builder.build(name, nullCtxA);
    PreviewCacheKey second = builder.build(name, nullCtxB);

    assertThat(first.inputHash()).isNotBlank();
    assertThat(first.inputHash()).isEqualTo(second.inputHash());
  }

  @Test
  void equalButDistinctObjectBodiesProduceEqualInputHashes() {
    String name = "unregistered-name-" + System.nanoTime();
    Map<String, Object> bodyA = Map.of("k", "v", "n", 42);
    Map<String, Object> bodyB = Map.of("k", "v", "n", 42);
    Context<?> ctxA = SimpleContext.builder(bodyA).mode(ExecutionMode.PREVIEW).runId("run-a")
            .build();
    Context<?> ctxB = SimpleContext.builder(bodyB).mode(ExecutionMode.PREVIEW).runId("run-b")
            .build();

    assertThat(bodyA).isNotSameAs(bodyB);

    PreviewCacheKey keyA = builder.build(name, ctxA);
    PreviewCacheKey keyB = builder.build(name, ctxB);

    assertThat(keyA.inputHash()).isEqualTo(keyB.inputHash());
    assertThat(keyA).isEqualTo(keyB);
  }

  @Test
  void differentNamesProduceDifferentKeysForSameBody() {
    Context<?> ctx = SimpleContext.builder("shared").mode(ExecutionMode.PREVIEW).runId("run-1")
            .build();
    String nameA = "name-a-" + System.nanoTime();
    String nameB = "name-b-" + System.nanoTime();

    PreviewCacheKey keyA = builder.build(nameA, ctx);
    PreviewCacheKey keyB = builder.build(nameB, ctx);

    assertThat(keyA.processName()).isEqualTo(nameA);
    assertThat(keyB.processName()).isEqualTo(nameB);
    assertThat(keyA).isNotEqualTo(keyB);
  }

  @Test
  void registeredHelperPopulatesDescriptorHash() {
    String helperName = "echo-helper-" + System.nanoTime();
    GlobalManager.globalManager().registerHelper(helperName, new EchoHelper());

    Context<?> ctx = SimpleContext.builder("payload").mode(ExecutionMode.PREVIEW).runId("run-1")
            .build();
    PreviewCacheKey registered = builder.build(helperName, ctx);

    String orphanName = "orphan-" + System.nanoTime();
    PreviewCacheKey orphan = builder.build(orphanName, ctx);

    assertThat(registered.dslDescriptorHash()).isNotBlank();
    assertThat(orphan.dslDescriptorHash()).isEmpty();
    assertThat(registered.dslDescriptorHash()).isNotEqualTo(orphan.dslDescriptorHash());
  }

  @Test
  void helperDescriptorHashIsDeterministic() {
    String helperName = "determinism-helper-" + System.nanoTime();
    GlobalManager.globalManager().registerHelper(helperName, new EchoHelper());

    Context<?> ctx = SimpleContext.builder("payload").mode(ExecutionMode.PREVIEW).runId("run-1")
            .build();
    PreviewCacheKeyBuilder otherBuilder = new PreviewCacheKeyBuilder();

    PreviewCacheKey first = builder.build(helperName, ctx);
    PreviewCacheKey second = otherBuilder.build(helperName, ctx);

    assertThat(first.dslDescriptorHash()).isNotEmpty();
    assertThat(first.dslDescriptorHash()).isEqualTo(second.dslDescriptorHash());
  }

  @Test
  void inputHashIsSha256HexLowercase64Chars() {
    String name = "unregistered-name-" + System.nanoTime();
    Context<?> ctx = SimpleContext.builder("payload").mode(ExecutionMode.PREVIEW).runId("run-1")
            .build();

    PreviewCacheKey key = builder.build(name, ctx);

    assertThat(key.inputHash()).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(key.dslDescriptorHash()).isEmpty();
    assertThat(key.processName()).isEqualTo(name);
  }

  private static final class EchoHelper implements Executable<Object, Object> {

    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("echo");
    }

    @Override
    public ExecutableDescriptor describe() {
      return new ExecutableDescriptor(
              "echo", "Echo helper", Object.class, Object.class,
              true, "delegates to execute", List.of());
    }
  }
}
