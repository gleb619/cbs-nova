package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.process.ProcessRichContext;
import cbs.nova.dsl.transaction.TransactionRichContext;
import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.helper.CompensationTrackerHelper;
import cbs.nova.starter.helper.UnreliableApiHelper;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class UnreliableApiDslExplainTest {

  @BeforeEach
  void loadCompactDsls() {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().helperInstanceResolver().replace(typedHelperResolver());
    new DefinitionLoader().load(GlobalManager.globalManager());
    GlobalManager.globalManager().registerHelperResolvers();
  }

  @AfterEach
  void cleanup() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void unreliableApiObjectsHaveDescriptions() {
    List<String> names = List.of(
            "unreliableApiTxResilient",
            "unreliableApiTxFragile",
            "UnreliableApiSuccess",
            "UnreliableApiCompensated",
            "UnreliableApiUncaught");

    for (String name : names) {
      assertThat(GlobalManager.globalManager().description(name))
              .as("description for %s", name)
              .isPresent()
              .get()
              .asString()
              .isNotBlank();
    }

    assertThat(GlobalManager.globalManager().description("unreliableApi"))
            .isPresent()
            .get()
            .asString()
            .contains("unreliable");
  }

  @Test
  void transactionsLoadExplainMarkdown() {
    for (String name : List.of("unreliableApiTxResilient", "unreliableApiTxFragile")) {
      var tx = GlobalManager.globalManager().findTransaction(name).orElseThrow();
      var ctx = new TransactionRichContext<>(
              SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-" + name)
                      .build());

      var result = tx.effectiveExplain().apply(ctx);

      assertThat(result.isSuccess()).as("explain success for %s", name).isTrue();
      assertThat(result.value().markdown())
              .as("explain body for %s", name)
              .contains("# " + name);
      assertThat(result.value().description()).isNotBlank();
    }
  }

  @Test
  void processesLoadExplainMarkdownAndMermaid() {
    for (String name : List.of("UnreliableApiSuccess", "UnreliableApiCompensated")) {
      var process = GlobalManager.globalManager().findProcess(name).orElseThrow();
      var ctx = new ProcessRichContext<>(
              SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-" + name)
                      .build());

      var result = process.explainLogic().apply(ctx);

      assertThat(result.isSuccess()).as("explain success for %s", name).isTrue();
      assertThat(result.value().markdown())
              .as("explain body for %s", name)
              .contains("# " + name)
              .contains("```mermaid");
      assertThat(result.value().description()).isNotBlank();
    }
  }

  @Test
  void uncaughtProcessLoadsExplainMarkdownWithoutMermaid() {
    var process = GlobalManager.globalManager().findProcess("UnreliableApiUncaught").orElseThrow();
    var ctx = new ProcessRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-uncaught")
                    .build());

    var result = process.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().markdown()).contains("# UnreliableApiUncaught");
    assertThat(result.value().markdown()).doesNotContain("```mermaid");
    assertThat(result.value().description()).isNotBlank();
  }

  @Test
  void helperExplainReturnsMarkdownWithMermaid() {
    var helper = GlobalManager.globalManager().findHelper("unreliableApi").orElseThrow();
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-helper")
            .build();

    var report = ((Executable<Object, ?>) helper).explain(ctx);

    assertThat(report.description())
            .contains("unreliable")
            .contains("CONSECUTIVE")
            .contains("RANDOM")
            .contains("```mermaid");
  }

  private static HelperInstanceResolver typedHelperResolver() {
    return helperClass -> {
      if (helperClass == UnreliableApiHelper.class) {
        return new UnreliableApiHelper(Caffeine.newBuilder()
                .expireAfterWrite(StarterConstants.UNRELIABLE_API_TTL)
                .maximumSize(StarterConstants.UNRELIABLE_API_MAX_SIZE)
                .build());
      }
      if (helperClass == CompensationTrackerHelper.class) {
        return new CompensationTrackerHelper(Caffeine.newBuilder()
                .expireAfterWrite(StarterConstants.COMPENSATION_TRACKER_TTL)
                .maximumSize(StarterConstants.COMPENSATION_TRACKER_MAX_SIZE)
                .build());
      }
      try {
        var constructor = helperClass.getDeclaredConstructor();
        if (!constructor.canAccess(null)) {
          constructor.setAccessible(true);
        }
        return (cbs.nova.dsl.Executable<?, ?>) constructor.newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot instantiate helper " + helperClass.getName(), e);
      }
    };
  }
}
