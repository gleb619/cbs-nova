package cbs.nova.dsl.example.integration;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.example.integration.PreviewDryRunTestConfig.PreviewSideEffectsHelper;
import cbs.nova.dsl.example.integration.PreviewDryRunTestConfig.PreviewSideEffectsIn;
import cbs.nova.dsl.example.integration.PreviewDryRunTestConfig.PreviewSideEffectsOut;
import cbs.nova.starter.StarterITApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-to-end integration test that executes the same DSL flow in RUN and PREVIEW modes.
 *
 * <p>
 * RUN mode performs real JDBC writes and a real Feign HTTP call. PREVIEW mode returns the same
 * output contract with a mocked value, captures a database and an HTTP external call, builds a
 * call-tree AST, and records dry-run logs.
 */
@SpringBootTest(classes = StarterITApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.sql.init.mode=never"
})
@Import(PreviewDryRunTestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PreviewDryRunIntegrationTest {

  @Autowired
  private DslRuntime dslRuntime;

  @Autowired
  private ContextFactory contextFactory;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private PreviewSideEffectsHelper previewSideEffectsHelper;

  @Autowired
  private AtomicInteger previewDryRunHttpRequestCount;

  @BeforeAll
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager().registerHelper("previewSideEffectsHelper",
            previewSideEffectsHelper);
    GlobalManager.globalManager()
            .registerProcess(
                    Dsl.process("PreviewSideEffects")
                            .input(PreviewSideEffectsIn.class)
                            .output(PreviewSideEffectsOut.class)
                            .execute(ctx -> ctx.runHelper("previewSideEffectsHelper"))
                            .build());
  }

  @AfterAll
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void previewModeReturnsPreviewReportWithCapturedCalls() {
    String requestId = "preview-" + System.currentTimeMillis();
    var input = new PreviewSideEffectsIn(requestId, "preview-payload");

    Result<PreviewReport> result = dslRuntime.preview("PreviewSideEffects",
            contextFactory.of(input, ExecutionMode.PREVIEW));

    assertThat(result.isSuccess()).as("preview result cause: %s", result.cause()).isTrue();
    PreviewReport report = result.value();
    assertThat(report.name()).isEqualTo("PreviewSideEffects");
    assertThat(report.mode()).isEqualTo(ExecutionMode.PREVIEW);
    assertThat(report.success()).isTrue();
    assertThat(report.output()).isInstanceOf(PreviewSideEffectsOut.class);
    PreviewSideEffectsOut out = (PreviewSideEffectsOut) report.output();
    assertThat(out.result()).isEqualTo("preview-mock");
    assertThat(out.source()).isEqualTo("none");

    assertThat(report.externalCalls())
            .as("preview should capture at least one database call")
            .anySatisfy(call -> assertThat(call).containsEntry("type", "database"));
    assertThat(report.externalCalls())
            .as("preview should capture at least one http call")
            .anySatisfy(call -> assertThat(call).containsEntry("type", "http"));

    assertThat(report.astTree()).as("astTree should be present").isNotNull();
    assertThat(report.astTree().name()).isEqualTo("PreviewSideEffects");
    assertThat(report.astTree().kind()).isEqualTo(CallKind.PROCESS);
    assertThat(report.astTree().children()).hasSize(1);
    assertThat(report.astTree().children().get(0).name()).isEqualTo("previewSideEffectsHelper");
    assertThat(report.astTree().children().get(0).kind()).isEqualTo(CallKind.HELPER);

    assertThat(report.dryRunLogs()).as("dryRunLogs should be present").isNotEmpty();
    assertThat(report.dryRunLogs())
            .anySatisfy(
                    log -> assertThat(log)
                            .containsEntry("message",
                                    "Preview dry-run for requestId=" + requestId));

    Integer rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM preview_dry_run WHERE request_id = ?", Integer.class, requestId);
    assertThat(rows).as("preview should not write a real DB row").isZero();
  }

  @Test
  void runModePerformsRealSideEffects() {
    int requestsBefore = previewDryRunHttpRequestCount.get();
    String requestId = "run-" + System.currentTimeMillis();
    var input = new PreviewSideEffectsIn(requestId, "run-payload");

    Result<?> result = dslRuntime.run("PreviewSideEffects",
            contextFactory.of(input, ExecutionMode.RUN));

    assertThat(result.isSuccess()).as("run result cause: %s", result.cause()).isTrue();
    assertThat(result.value()).isInstanceOf(PreviewSideEffectsOut.class);
    PreviewSideEffectsOut out = (PreviewSideEffectsOut) result.value();
    assertThat(out.result()).isEqualTo("real:" + requestId);
    assertThat(out.source()).isEqualTo("http:stubbed");
    assertThat(result.value()).isNotInstanceOf(PreviewReport.class);

    Integer rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM preview_dry_run WHERE request_id = ?", Integer.class, requestId);
    assertThat(rows).as("run should write a real DB row").isEqualTo(1);

    assertThat(previewDryRunHttpRequestCount.get() - requestsBefore)
            .as("run should make exactly one real HTTP request")
            .isEqualTo(1);
  }
}
