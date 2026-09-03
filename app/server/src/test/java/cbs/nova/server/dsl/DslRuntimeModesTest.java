package cbs.nova.server.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.app.dsl.OrderIn;
import cbs.nova.app.dsl.OrderOut;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.config.ContextFactory;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DslRuntimeModesTest {

  @Autowired
  private DslRuntime dslRuntime;

  @Autowired
  private ContextFactory contextFactory;

  @BeforeAll
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    new DefinitionLoader().load(GlobalManager.globalManager());
  }

  @Test
  void definitionsAreRegistered() {
    assertThat(GlobalManager.globalManager().findProcess("OrderProcess")).isPresent();
    assertThat(GlobalManager.globalManager().findTransaction("VALIDATE_ORDER")).isPresent();
  }

  @Test
  void previewAcceptsValidOrder() {
    var input = new OrderIn("C-123", new BigDecimal("100.00"), "P-1");
    Result<PreviewReport> result = dslRuntime.preview("OrderProcess",
        contextFactory.of(input, ExecutionMode.PREVIEW));

    assertThat(result.isSuccess()).as("preview result cause: %s", result.cause()).isTrue();
    PreviewReport report = result.value();
    assertThat(report).isNotNull();
    assertThat(report.success()).isTrue();
    assertThat(report.output()).isInstanceOf(OrderOut.class);
    OrderOut out = (OrderOut) report.output();
    assertThat(out.status()).isEqualTo("ACCEPTED");
  }

  @Test
  void previewRejectsInvalidOrder() {
    var input = new OrderIn("", BigDecimal.ZERO, "P-1");
    Result<PreviewReport> result = dslRuntime.preview("OrderProcess",
        contextFactory.of(input, ExecutionMode.PREVIEW));

    assertThat(result.isSuccess()).as("preview result cause: %s", result.cause()).isTrue();
    PreviewReport report = result.value();
    assertThat(report).isNotNull();
    assertThat(report.output()).isInstanceOf(OrderOut.class);
    OrderOut out = (OrderOut) report.output();
    assertThat(out.status()).isEqualTo("REJECTED");
  }

  @Test
  void explainReturnsReport() {
    var input = new OrderIn("C-123", new BigDecimal("100.00"), "P-1");
    ExplainReport report = dslRuntime.explain("OrderProcess",
        contextFactory.of(input, ExecutionMode.EXPLAIN));

    assertThat(report).isNotNull();
    assertThat(report.description()).containsIgnoringCase("order");
    assertThat(report.executionTrace()).isNotEmpty();
  }

  @Test
  void runInvokesProcess() {
    var input = new OrderIn("C-123", new BigDecimal("100.00"), "P-1");
    Result<?> result = dslRuntime.run("OrderProcess",
        contextFactory.of(input, ExecutionMode.RUN));

    assertThat(result.isSuccess()).as("run result cause: %s", result.cause()).isTrue();
    OrderOut out = result.as(OrderOut.class);
    assertThat(out).isNotNull();
    assertThat(out.status()).isEqualTo("ACCEPTED");
  }
}
