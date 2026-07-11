package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class DslHealthIndicatorTest {

  @BeforeEach
  void setUp() {
    GlobalManager.getInstance().resetForTests();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.getInstance().resetForTests();
  }

  @Test
  void healthIsUpWhenEmpty() {
    var indicator = new DslHealthIndicator();
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void healthDetailsReportCounts() {
    GlobalManager.getInstance()
            .registerProcess(
                    Dsl.process("Loan").execute(ctx -> Result.success("ok")).build());
    var indicator = new DslHealthIndicator();
    var details = indicator.health().getDetails();
    assertThat(details.get("processes")).isEqualTo(1);
    assertThat(details.get("transactions")).isEqualTo(0);
    assertThat(details.get("helpers")).isEqualTo(0);
  }
}
