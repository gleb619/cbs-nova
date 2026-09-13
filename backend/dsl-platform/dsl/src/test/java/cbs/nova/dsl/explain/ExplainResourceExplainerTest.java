package cbs.nova.dsl.explain;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExplainResourceExplainerTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @AfterEach
  void resetResolverOverride() {
    DslConfig.dslConfig().explainResourceResolver().replace(null);
  }

  @Test
  void loadsMarkdownIntoReportDescription() {
    var explain = ExplainResourceExplainer.viaResource("DocSample", "builder-sample.md");
    var ctx = explainContext(Map.of());

    var result = explain.apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().name()).isEqualTo("DocSample");
    assertThat(result.value().description()).contains("# Builder Sample");
    assertThat(result.value().mermaid()).isEmpty();
  }

  @Test
  void acceptsPrefixedResourcePath() {
    var explain = ExplainResourceExplainer.viaResource("DocSample", "explain/builder-sample.md");

    var result = explain.apply(explainContext(Map.of()));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().description()).contains("# Builder Sample");
  }

  @Test
  void missingResourceFailsAtInvocationNotAtComposition() {
    var explain = ExplainResourceExplainer.viaResource("Missing", "does-not-exist.md");

    var result = explain.apply(explainContext(Map.of()));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("classpath: explain/does-not-exist.md");
  }

  @Test
  void truncatesMarkdownToMetadataBudget() {
    var explain = ExplainResourceExplainer.viaResource("DocSample", "builder-sample.md");
    var bounded = explainContext(Map.of(Constants.EXPLAIN_BUDGET_CHARS_KEY, 10));

    var result = explain.apply(bounded);

    assertThat(result.value().description()).hasSizeLessThanOrEqualTo(10);
  }

  @Test
  void invocationResolvesReplacableResolverLazily() {
    var explain = ExplainResourceExplainer.viaResource("DocSample", "anything.md");
    DslConfig.dslConfig().explainResourceResolver().replace(_path -> "REPLACED-CONTENT");

    var result = explain.apply(explainContext(Map.of()));

    assertThat(result.value().description()).isEqualTo("REPLACED-CONTENT");
  }

  private Context<?> explainContext(Map<String, Object> metadata) {
    return metadata.isEmpty()
            ? contextFactory.of("body", ExecutionMode.EXPLAIN, "run-doc")
            : contextFactory.of("body", metadata, ExecutionMode.EXPLAIN, "run-doc");
  }
}
