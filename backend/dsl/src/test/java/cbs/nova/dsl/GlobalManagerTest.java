package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlobalManagerTest {
  @BeforeEach
  void reset() {
    GlobalManager.resetForTests();
  }

  @Test
  void endToEndProcessPreview() {
    var gm = GlobalManager.getInstance();
    gm.registerProcess(
            Dsl.process("Greet")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("Hello, " + ctx.body()))
                    .build());
    var ctx = SimpleContext.of("World", ExecutionMode.PREVIEW);
    var result = gm.runProcess("Greet", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("Hello, World");
  }

  @Test
  void unknownProcessReturnsFailure() {
    var result = GlobalManager.getInstance()
            .runProcess("Ghost", SimpleContext.of("x", ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void helperRoundTrip() {
    var gm = GlobalManager.getInstance();
    gm.registerHelper("upper", ctx -> Result.success(ctx.body().toString().toUpperCase()));
    var result = gm.runHelper("upper", SimpleContext.of("hello", ExecutionMode.PREVIEW));
    assertThat(result.value()).isEqualTo("HELLO");
  }
}
