package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

class ExplainSupportTest {

  private final Context<String> ctx = new StubContext("body");

  @Test
  void defaultBudgetIsFourThousand() {
    assertThat(ExplainSupport.DEFAULT_BUDGET_CHARS).isEqualTo(4_000);
  }

  @Test
  void defaultOverloadDelegatesWithDefaultBudget() {
    var budgetSeen = new AtomicInteger(-1);
    ExplainSupport<String, String> support = new ExplainSupport<>() {
      @Override
      public String explain(Context<String> ctx, int budgetChars) {
        budgetSeen.set(budgetChars);
        return "ok";
      }
    };

    assertThat(support.explain(ctx)).isEqualTo("ok");
    assertThat(budgetSeen).hasValue(ExplainSupport.DEFAULT_BUDGET_CHARS);
  }

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

    var report = executable.explain(ctx, ExplainSupport.DEFAULT_BUDGET_CHARS);

    assertThat(report.description()).isEqualTo("Greets the caller.");
    assertThat(report.mermaid()).isEmpty();
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
                "lengthOf", null, String.class, Integer.class, false, null, List.of());
      }
    };

    var report = executable.explain(ctx, ExplainSupport.DEFAULT_BUDGET_CHARS);

    assertThat(report.name()).isEqualTo("lengthOf");
    assertThat(report.description())
            .contains("`String`")
            .contains("`Integer`")
            .contains("side-effect free");
  }

  @Test
  void executableExplainFallsBackToClassNameForMissingDescriptorName() {
    Executable<String, String> executable = new NamedExecutable();

    var report = executable.explain(ctx, ExplainSupport.DEFAULT_BUDGET_CHARS);

    assertThat(report.name()).isEqualTo("NamedExecutable");
  }

  @Test
  void executableExplainTruncatesDescriptionToBudget() {
    Executable<String, String> executable = ctx -> Result.success("ok");

    var report = executable.explain(ctx, 50);

    assertThat(report.description().length()).isLessThanOrEqualTo(50);
  }

  @Test
  void truncateToBudgetLeavesShortTextUnchanged() {
    assertThat(ExplainSupport.truncateToBudget("abc", 3)).isEqualTo("abc");
    assertThat(ExplainSupport.truncateToBudget("abc", 10)).isEqualTo("abc");
    assertThat(ExplainSupport.truncateToBudget("abcdef", 3)).isEqualTo("abc");
    assertThat(ExplainSupport.truncateToBudget("abcdef", -1)).isEmpty();
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

    @Override
    public String body() {
      return body;
    }

    @Override
    public Map<String, Object> metadata() {
      return Map.of();
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
      return (Context<U>) new StubContext(String.valueOf(body));
    }

    @Override
    public Context<String> withMetadata(String key, Object value) {
      return this;
    }
  }
}
