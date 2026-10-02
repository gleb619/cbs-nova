package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.dslexamples.v1.PreviewExplainModels.LineItem;
import cbs.nova.dslexamples.v1.PreviewExplainModels.PreviewExplainIn;
import cbs.nova.dslexamples.v1.PreviewExplainModels.PreviewExplainOut;
import cbs.nova.starter.helper.CompensationTrackerHelper;
import cbs.nova.starter.helper.CronParseHelper;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T772 smoke tests for the {@code PreviewExplainDsl} example.
 *
 * <p>
 * Mirrors the {@link AdvancedDslExamplesTest} pattern: a fixed input removes wall-clock dependence
 * and exercises all three execution paths (run, preview, explain). The {@code persistPlan}
 * transaction is registered as a real Temporal transaction in generated code, but the
 * {@code runTransaction(...)} call in this test resolves to the dry-run fallback because the
 * registry only has the dummy {@code ProcessRunner} wired (no Temporal cluster), so we instead
 * drive {@code execute()} vs. {@code preview()} via the {@link ExecutionMode} on the
 * {@link Context}. The static counter in {@code PreviewExplainModels.PERSIST_COUNTER} is the single
 * source of truth that proves preview/explain modes never reached the side-effecting
 * {@code execute} body.
 */
class PreviewExplainDslTest {

  @BeforeEach
  void loadCompactDsls() {
    // Reset the counter so each test starts at zero — otherwise a previous run would
    // contaminate the "preview/explain do not increment" assertion below.
    cbs.nova.dslexamples.v1.PreviewExplainModels.PERSIST_COUNTER.set(0);
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().helperInstanceResolver().replace(typedHelperResolver());
    DslConfig.dslConfig().expressionEvaluator()
            .replace(new cbs.nova.dsl.utils.MvelExpressionEvaluator());
    new DefinitionLoader().load(GlobalManager.globalManager());
    GlobalManager.globalManager().registerHelperResolvers();
  }

  @AfterEach
  void cleanup() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void previewExplainRunModePersistsPlanAndIncrementsCounter() {
    // Two line items, 10% discount: 100*1 + 50*2 = 200; discount 20; tax on 180 = 36; total 216.
    var input = new PreviewExplainIn("run-A", List.of(
            new LineItem("SKU-1", "Widget", 100.0, 1),
            new LineItem("SKU-2", "Gadget", 50.0, 2)),
            0.10);
    Context<PreviewExplainIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.RUN)
            .build();

    int before = cbs.nova.dslexamples.v1.PreviewExplainModels.PERSIST_COUNTER.get();
    Result<?> result = GlobalManager.globalManager().runProcess("PreviewExplain", ctx);
    int after = cbs.nova.dslexamples.v1.PreviewExplainModels.PERSIST_COUNTER.get();

    // Run mode actually invokes the persistPlan transaction's execute() body, so
    // the counter must have moved forward by exactly one (one persistPlan call).
    assertThat(after - before).as("PERSIST_COUNTER did not increment in run mode")
            .isEqualTo(1);
    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    PreviewExplainOut out = (PreviewExplainOut) result.value();
    assertThat(out.orderId()).isEqualTo("run-A");
    assertThat(out.plan()).hasSize(2);
    assertThat(out.subtotal()).isEqualTo(200.0);
    assertThat(out.discount()).isEqualTo(20.0);
    assertThat(out.tax()).isEqualTo(36.0);
    assertThat(out.total()).isEqualTo(216.0);
  }

  @Test
  void previewExplainPreviewModeReturnsCustomPayloadAndDoesNotIncrementCounter() {
    var input = new PreviewExplainIn("preview-A", List.of(
            new LineItem("SKU-1", "Widget", 100.0, 1),
            new LineItem("SKU-2", "Gadget", 50.0, 2)),
            0.10);
    Context<PreviewExplainIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    int before = cbs.nova.dslexamples.v1.PreviewExplainModels.PERSIST_COUNTER.get();
    Result<?> result = GlobalManager.globalManager().runProcess("PreviewExplain", ctx);
    int after = cbs.nova.dslexamples.v1.PreviewExplainModels.PERSIST_COUNTER.get();

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    // Custom preview() must run — execute() must not — so the counter does not move.
    assertThat(after).as("PERSIST_COUNTER changed in preview mode").isEqualTo(before);
    PreviewExplainOut out = (PreviewExplainOut) result.value();
    assertThat(out.orderId()).isEqualTo("preview-A");
    assertThat(out.plan()).hasSize(2);
    assertThat(out.subtotal()).isEqualTo(200.0);
    assertThat(out.discount()).isEqualTo(20.0);
    assertThat(out.tax()).isEqualTo(36.0);
    assertThat(out.total()).isEqualTo(216.0);
  }

  @Test
  void previewExplainExplainModeReturnsCodeBuiltExplainReport() {
    var input = new PreviewExplainIn("explain-A", List.of(
            new LineItem("SKU-1", "Widget", 100.0, 1)),
            0.0);
    Context<PreviewExplainIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.EXPLAIN)
            .build();

    int before = cbs.nova.dslexamples.v1.PreviewExplainModels.PERSIST_COUNTER.get();
    Result<?> result = GlobalManager.globalManager().runProcess("PreviewExplain", ctx);
    int after = cbs.nova.dslexamples.v1.PreviewExplainModels.PERSIST_COUNTER.get();

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    // explain() runs in code — never touches execute() or persistPlan's execute body.
    assertThat(after).as("PERSIST_COUNTER changed in explain mode").isEqualTo(before);
    assertThat(result.value()).isInstanceOf(ExplainReport.class);
    ExplainReport report = (ExplainReport) result.value();
    assertThat(report.name()).isEqualTo("PreviewExplain");
    assertThat(report.description()).contains("Compute a price plan");
    // Built in code — markdown body must contain the explain-mode input echo.
    assertThat(report.markdown()).contains("PreviewExplain (process)")
            .contains("explain-A")
            .contains("subtotal: 100.00");
    // The persistPlan transaction is wired as a child section so reviewers see the
    // side-effect surface from the explain call alone.
    assertThat(report.children()).hasSize(1);
    assertThat(report.children().get(0).name()).isEqualTo("persistPlan");
    assertThat(report.children().get(0).description()).contains("Side-effecting");
  }

  private static HelperInstanceResolver typedHelperResolver() {
    return helperClass -> {
      if (helperClass == CronParseHelper.class) {
        return new CronParseHelper();
      }
      if (helperClass == CompensationTrackerHelper.class) {
        return new CompensationTrackerHelper(Caffeine.newBuilder()
                .expireAfterWrite(cbs.nova.starter.core.StarterConstants.COMPENSATION_TRACKER_TTL)
                .maximumSize(cbs.nova.starter.core.StarterConstants.COMPENSATION_TRACKER_MAX_SIZE)
                .build());
      }
      if (helperClass == cbs.nova.starter.helper.UnreliableApiHelper.class) {
        return new cbs.nova.starter.helper.UnreliableApiHelper(Caffeine.newBuilder()
                .expireAfterWrite(cbs.nova.starter.core.StarterConstants.UNRELIABLE_API_TTL)
                .maximumSize(cbs.nova.starter.core.StarterConstants.UNRELIABLE_API_MAX_SIZE)
                .build());
      }
      try {
        return (cbs.nova.dsl.Executable<?, ?>) helperClass.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot instantiate helper " + helperClass.getName(), e);
      }
    };
  }
}
