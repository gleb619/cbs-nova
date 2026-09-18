package cbs.nova.dsl.explain;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.BeanResolver;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.config.DslConfig;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExplainResourceExplainerTest {

  @AfterEach
  void resetResolverOverride() {
    DslConfig.dslConfig().beanResolver().replace(null);
  }

  @Test
  void loadsMarkdownBodyIntoReportMermaidAndFrontmatterIntoDescription() {
    var explain = ExplainResourceExplainer.viaResource("DocSample", "builder-sample.md");
    var ctx = explainContext(Map.of());

    var result = explain.apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().name()).isEqualTo("DocSample");
    assertThat(result.value().mermaid()).contains("# Builder Sample");
    assertThat(result.value().description())
            .isEqualTo("Markdown backing the explainVia builder tests.");
  }

  @Test
  void acceptsPrefixedResourcePath() {
    var explain = ExplainResourceExplainer.viaResource("DocSample", "explain/builder-sample.md");

    var result = explain.apply(explainContext(Map.of()));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().mermaid()).contains("# Builder Sample");
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
  void loadsFullMarkdownRegardlessOfMetadataBudget() {
    var explain = ExplainResourceExplainer.viaResource("DocSample", "builder-sample.md");
    var bounded = explainContext(Map.of(Constants.EXPLAIN_BUDGET_CHARS_KEY, 10));

    var result = explain.apply(bounded);

    assertThat(result.value().mermaid())
            .contains("# Builder Sample")
            .hasSizeGreaterThan(10);
  }

  @Test
  void invocationResolvesReplacableResolverLazily() {
    var explain = ExplainResourceExplainer.viaResource("DocSample", "anything.md");
    BeanResolver resolver = type -> {
      if (type == ExplainResourceResolver.class) {
        return (ExplainResourceResolver) _path -> "REPLACED-CONTENT";
      }
      throw new IllegalStateException("Unexpected bean type: " + type);
    };
    DslConfig.dslConfig().beanResolver().replace(resolver);

    var result = explain.apply(explainContext(Map.of()));

    assertThat(result.value().mermaid()).isEqualTo("REPLACED-CONTENT");
  }

  @Test
  void invocationUsesContextLocalBeanResolver() {
    var explain = ExplainResourceExplainer.viaResource("DocSample", "anything.md");
    BeanResolver resolver = type -> {
      if (type == ExplainResourceResolver.class) {
        return (ExplainResourceResolver) _path -> "CONTEXT-LOCAL-CONTENT";
      }
      throw new IllegalStateException("Unexpected bean type: " + type);
    };
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-local")
            .build()
            .withBeanResolver(resolver);

    var result = explain.apply(ctx);

    assertThat(result.value().mermaid()).isEqualTo("CONTEXT-LOCAL-CONTENT");
  }

  private Context<?> explainContext(Map<String, Object> metadata) {
    return metadata.isEmpty()
            ? SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-doc")
                    .build()
            : SimpleContext.builder().body("body").metadata(metadata).mode(ExecutionMode.EXPLAIN)
                    .runId("run-doc").build();
  }
}
