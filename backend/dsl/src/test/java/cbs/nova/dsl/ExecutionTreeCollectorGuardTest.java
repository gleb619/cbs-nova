package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExecutionTreeCollectorGuardTest {

  @Test
  void treeAtMaxDepthBuiltWithoutTruncation() {
    var collector = new ExecutionTreeCollector(2);
    collector.startRun("r");
    collector.onProcessStart("r", "p", null);
    collector.onHelperStart("r", "h", null);
    collector.onHelperEnd("r", "h", null, true);
    collector.onProcessEnd("r", "p", null, true);
    collector.finishRun("r");

    var root = collector.tree("r").orElseThrow();
    assertThat(root.name()).isEqualTo("p");
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).name()).isEqualTo("h");
    assertThat(root.children().get(0).kind()).isEqualTo(CallKind.HELPER);
  }

  @Test
  void treeBeyondMaxDepthTruncatesLeaf() {
    var collector = new ExecutionTreeCollector(2);
    collector.startRun("r");
    collector.onProcessStart("r", "p", null);
    collector.onHelperStart("r", "h", null);
    collector.onFunctionStart("r", "f", null);
    collector.onFunctionEnd("r", "f", null, false);
    collector.onHelperEnd("r", "h", null, true);
    collector.onProcessEnd("r", "p", null, true);
    collector.finishRun("r");

    var root = collector.tree("r").orElseThrow();
    assertThat(root.children()).hasSize(1);
    var helper = root.children().get(0);
    assertThat(helper.children()).hasSize(1);
    var truncated = helper.children().get(0);
    assertThat(truncated.name()).isEqualTo("<truncated>");
    assertThat(truncated.kind()).isEqualTo(CallKind.FUNCTION);
    assertThat(truncated.success()).isFalse();
    assertThat(truncated.children()).isEmpty();
    assertThat(truncated.externalCalls()).isEmpty();
  }

  @Test
  void cycleDetectsRepeatedNameKind() {
    var collector = new ExecutionTreeCollector();
    collector.startRun("r");
    collector.onProcessStart("r", "p", null);
    collector.onHelperStart("r", "a", null);
    collector.onHelperStart("r", "b", null);
    collector.onHelperStart("r", "a", null);
    collector.onHelperEnd("r", "a", null, false);
    collector.onHelperEnd("r", "b", null, true);
    collector.onHelperEnd("r", "a", null, true);
    collector.onProcessEnd("r", "p", null, true);
    collector.finishRun("r");

    var root = collector.tree("r").orElseThrow();
    var a = root.children().get(0);
    assertThat(a.name()).isEqualTo("a");
    var b = a.children().get(0);
    assertThat(b.name()).isEqualTo("b");
    var truncated = b.children().get(0);
    assertThat(truncated.name()).isEqualTo("<truncated>");
    assertThat(truncated.kind()).isEqualTo(CallKind.HELPER);
    assertThat(truncated.success()).isFalse();
  }

  @Test
  void concurrentRunIdsAreIsolated() {
    var collector = new ExecutionTreeCollector();
    collector.startRun("r1");
    collector.onProcessStart("r1", "p", null);
    collector.onHelperStart("r1", "a", null);
    collector.onHelperStart("r1", "b", null);
    collector.onHelperStart("r1", "a", null);
    collector.onHelperEnd("r1", "a", null, false);
    collector.onHelperEnd("r1", "b", null, true);
    collector.onHelperEnd("r1", "a", null, true);
    collector.onProcessEnd("r1", "p", null, true);
    collector.finishRun("r1");

    collector.startRun("r2");
    collector.onProcessStart("r2", "p", null);
    collector.onHelperStart("r2", "a", null);
    collector.onHelperStart("r2", "b", null);
    collector.onHelperEnd("r2", "b", null, true);
    collector.onHelperEnd("r2", "a", null, true);
    collector.onProcessEnd("r2", "p", null, true);
    collector.finishRun("r2");

    var r1 = collector.tree("r1").orElseThrow();
    assertThat(r1.children().get(0).children().get(0).children().get(0).name())
            .isEqualTo("<truncated>");

    var r2 = collector.tree("r2").orElseThrow();
    assertThat(r2.children()).hasSize(1);
    assertThat(r2.children().get(0).children()).hasSize(1);
    assertThat(r2.children().get(0).children().get(0).name()).isEqualTo("b");
  }

  @Test
  void finishRunClearsCycleSet() {
    var collector = new ExecutionTreeCollector();
    collector.startRun("r1");
    collector.onProcessStart("r1", "p", null);
    collector.onHelperStart("r1", "a", null);
    collector.onHelperStart("r1", "a", null);
    collector.onHelperEnd("r1", "a", null, false);
    collector.onHelperEnd("r1", "a", null, true);
    collector.onProcessEnd("r1", "p", null, true);
    collector.finishRun("r1");

    collector.startRun("r1");
    collector.onProcessStart("r1", "p", null);
    collector.onHelperStart("r1", "a", null);
    collector.onHelperEnd("r1", "a", null, true);
    collector.onProcessEnd("r1", "p", null, true);
    collector.finishRun("r1");

    var root = collector.tree("r1").orElseThrow();
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).name()).isEqualTo("a");
    assertThat(root.children().get(0).children()).isEmpty();
  }

  @Test
  void maxDepthOneTruncatesEveryChild() {
    var collector = new ExecutionTreeCollector(1);
    collector.startRun("r");
    collector.onProcessStart("r", "p", null);
    collector.onHelperStart("r", "h", null);
    collector.onHelperEnd("r", "h", null, false);
    collector.onProcessEnd("r", "p", null, true);
    collector.finishRun("r");

    var root = collector.tree("r").orElseThrow();
    assertThat(root.name()).isEqualTo("p");
    assertThat(root.children()).hasSize(1);
    var truncated = root.children().get(0);
    assertThat(truncated.name()).isEqualTo("<truncated>");
    assertThat(truncated.kind()).isEqualTo(CallKind.HELPER);
    assertThat(truncated.success()).isFalse();
  }
}
