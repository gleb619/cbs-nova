package cbs.nova.dsl.explain;

import static cbs.nova.dsl.config.Constants.DEFAULT_BUDGET_CHARS;
import static cbs.nova.dsl.config.Constants.EXPLAIN_BUDGET_CHARS_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

class ExplainBudgetTest {

  private final StubContext ctx = new StubContext(Map.of());

  @Test
  void fallsBackToDefaultWhenMetadataAbsent() {
    assertThat(ExplainBudget.of(ctx)).isEqualTo(DEFAULT_BUDGET_CHARS);
  }

  @Test
  void readsNumericMetadata() {
    assertThat(ExplainBudget.of(new StubContext(Map.of(EXPLAIN_BUDGET_CHARS_KEY, 123))))
            .isEqualTo(123);
  }

  @Test
  void parsesTextMetadata() {
    assertThat(ExplainBudget.of(new StubContext(Map.of(EXPLAIN_BUDGET_CHARS_KEY, "77"))))
            .isEqualTo(77);
  }

  @Test
  void ignoresInvalidMetadata() {
    assertThat(ExplainBudget.of(new StubContext(Map.of(EXPLAIN_BUDGET_CHARS_KEY, "junk"))))
            .isEqualTo(DEFAULT_BUDGET_CHARS);
    assertThat(ExplainBudget.of(new StubContext(Map.of(EXPLAIN_BUDGET_CHARS_KEY, -5))))
            .isEqualTo(DEFAULT_BUDGET_CHARS);
  }

  @RequiredArgsConstructor
  private static final class StubContext implements Context<String> {
    private final Map<String, Object> metadata;

    @Override
    public String body() {
      return "body";
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
      return (Context<U>) new StubContext(metadata);
    }

    @Override
    public Context<String> withMetadata(String key, Object value) {
      return new StubContext(Map.of(key, value));
    }
  }
}
