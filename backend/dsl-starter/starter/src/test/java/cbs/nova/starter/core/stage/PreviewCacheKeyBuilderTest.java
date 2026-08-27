package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.cache.PreviewCacheKey;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreviewCacheKeyBuilderTest {

  private final ContextFactory contextFactory = new ContextFactory();
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
    Context<?> ctx = contextFactory.of("payload", cbs.nova.dsl.ExecutionMode.PREVIEW, "run-1");
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
    Context<?> ctxA = contextFactory.of("alpha", cbs.nova.dsl.ExecutionMode.PREVIEW, "run-a");
    Context<?> ctxB = contextFactory.of("beta", cbs.nova.dsl.ExecutionMode.PREVIEW, "run-b");

    PreviewCacheKey keyA = builder.build(name, ctxA);
    PreviewCacheKey keyB = builder.build(name, ctxB);

    assertThat(keyA.inputHash()).isNotEqualTo(keyB.inputHash());
    assertThat(keyA.processName()).isEqualTo(keyB.processName());
  }

  @Test
  void nameFlowsThroughUnchanged() {
    String name = "DistinctProcessName-" + System.nanoTime();
    Context<?> ctx = contextFactory.of("payload", cbs.nova.dsl.ExecutionMode.PREVIEW, "run-1");

    PreviewCacheKey key = builder.build(name, ctx);

    assertThat(key.processName()).isEqualTo(name);
  }

  @Test
  void nullBodyStillHashesDeterministically() {
    String name = "unregistered-name-" + System.nanoTime();
    Context<Object> nullCtxA = contextFactory.of(null, cbs.nova.dsl.ExecutionMode.PREVIEW, "run-a");
    Context<Object> nullCtxB = contextFactory.of(null, cbs.nova.dsl.ExecutionMode.PREVIEW, "run-b");

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
    Context<?> ctxA = contextFactory.of(bodyA, cbs.nova.dsl.ExecutionMode.PREVIEW, "run-a");
    Context<?> ctxB = contextFactory.of(bodyB, cbs.nova.dsl.ExecutionMode.PREVIEW, "run-b");

    assertThat(bodyA).isNotSameAs(bodyB);

    PreviewCacheKey keyA = builder.build(name, ctxA);
    PreviewCacheKey keyB = builder.build(name, ctxB);

    assertThat(keyA.inputHash()).isEqualTo(keyB.inputHash());
    assertThat(keyA).isEqualTo(keyB);
  }

  @Test
  void differentNamesProduceDifferentKeysForSameBody() {
    Context<?> ctx = contextFactory.of("shared", cbs.nova.dsl.ExecutionMode.PREVIEW, "run-1");
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

    Context<?> ctx = contextFactory.of("payload", cbs.nova.dsl.ExecutionMode.PREVIEW, "run-1");
    PreviewCacheKey registered = builder.build(helperName, ctx);

    String orphanName = "orphan-" + System.nanoTime();
    PreviewCacheKey orphan = builder.build(orphanName, ctx);

    assertThat(registered.dslDescriptorHash()).isNotBlank();
    assertThat(orphan.dslDescriptorHash()).isEmpty();
    assertThat(registered.dslDescriptorHash()).isNotEqualTo(orphan.dslDescriptorHash());
  }

  @Test
  void inputHashIsSha256HexLowercase64Chars() {
    String name = "unregistered-name-" + System.nanoTime();
    Context<?> ctx = contextFactory.of("payload", cbs.nova.dsl.ExecutionMode.PREVIEW, "run-1");

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
    public cbs.nova.dsl.ExecutableDescriptor describe() {
      return new cbs.nova.dsl.ExecutableDescriptor(
              "echo", "Echo helper", Object.class, Object.class,
              true, "delegates to execute", List.of());
    }
  }
}
