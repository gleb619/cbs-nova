package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.utils.ExplainReports;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

class ExplainSupportTest {

  private final Context<String> ctx = new StubContext("body", Map.of());

  @Test
  void executableExplainUsesDescriptionWhenPresent() {
    Executable<String, String> executable = new Executable<>() {
      @Override
      public Result<String> execute(Context<String> ctx) {
        return Result.success("ok");
      }

      @Override
      public String description() {
        return "Greets the caller.";
      }
    };

    var report = executable.explain(ctx);

    assertThat(report.description()).isEqualTo("Greets the caller.");
    assertThat(report.markdown()).isEqualTo("Greets the caller.");
    assertThat(report.name()).isNotBlank();
  }

  @Test
  void executableExplainDerivesDescriptionFromDescriptor() {
    Executable<String, Integer> executable = new Executable<>() {
      @Override
      public Result<Integer> execute(Context<String> ctx) {
        return Result.success(1);
      }

      @Override
      public ExecutableDescriptor describe() {
        return new ExecutableDescriptor(
                "lengthOf", null, String.class, Integer.class, List.of());
      }
    };

    var report = executable.explain(ctx);

    assertThat(report.name()).isEqualTo("lengthOf");
    assertThat(report.description())
            .contains("`String`")
            .contains("`Integer`")
            .contains("side-effect free");
  }

  @Test
  void executableExplainFallsBackToClassNameForMissingDescriptorName() {
    Executable<String, String> executable = new NamedExecutable();

    var report = executable.explain(ctx);

    assertThat(report.name()).isEqualTo("NamedExecutable");
  }

  @Test
  void executableExplainDoesNotTruncateReportToMetadataBudget() {
    Executable<String, String> executable = ctx -> Result.success("ok");
    var bounded = new StubContext("body", Map.of("explain.budgetChars", 50));

    var report = executable.explain(bounded);

    assertThat(report.description().length()).isGreaterThan(50);
    assertThat(report.description())
            .contains("Helper `null`")
            .contains("untyped")
            .contains("has side effects");
  }

  @Test
  void explainReportMergeCombinesDescriptionsAndDiagrams() {
    var left = ExplainReport.builder().name("n").description("left-desc").markdown("left-diagram")
            .build();
    var right = ExplainReport.builder().name("n").description("right-desc")
            .markdown("right-diagram")
            .build();
    var merged = ExplainReports.merge(left, right);
    assertThat(merged.description()).contains("left-desc").contains("right-desc");
    assertThat(merged.markdown()).contains("left-diagram").contains("right-diagram");
  }

  private static final class NamedExecutable implements Executable<String, String> {
    @Override
    public Result<String> execute(Context<String> ctx) {
      return Result.success("ok");
    }
  }

  @RequiredArgsConstructor
  private static final class StubContext implements Context<String> {
    private final String body;
    private final Map<String, Object> metadata;

    @Override
    public String body() {
      return body;
    }

    @Override
    public Map<String, Object> metadata() {
      return metadata;
    }

    @Override
    public ExecutionMode mode() {
      return ExecutionMode.EXPLAIN;
    }

    @Override
    public String runId() {
      return "run-test";
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U> Context<U> withBody(U body) {
      return (Context<U>) new StubContext(String.valueOf(body), metadata);
    }

    @Override
    public Context<String> withMetadata(String key, Object value) {
      return new StubContext(body, Map.of(key, value));
    }
  }
}
