package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExecutionTreeCollectorGuardTest {

  @Test
  void treeAtMaxDepthBuiltWithoutTruncation() {
    var collector = new ExecutionTreeCollector(2);
    collector.start();
    collector.onProcessStart("r", "p", null);
    collector.onHelperStart("r", "h", null);
    collector.onHelperEnd("r", "h", null, true);
    collector.onProcessEnd("r", "p", null, true);
    collector.finish();

    var root = collector.tree().orElseThrow();
    assertThat(root.name()).isEqualTo("p");
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).name()).isEqualTo("h");
    assertThat(root.children().get(0).kind()).isEqualTo(CallKind.HELPER);
  }

  @Test
  void treeBeyondMaxDepthTruncatesLeaf() {
    var collector = new ExecutionTreeCollector(2);
    collector.start();
    collector.onProcessStart("r", "p", null);
    collector.onHelperStart("r", "h", null);
    collector.onFunctionStart("r", "f", null);
    collector.onFunctionEnd("r", "f", null, false);
    collector.onHelperEnd("r", "h", null, true);
    collector.onProcessEnd("r", "p", null, true);
    collector.finish();

    var root = collector.tree().orElseThrow();
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
    collector.start();
    collector.onProcessStart("r", "p", null);
    collector.onHelperStart("r", "a", null);
    collector.onHelperStart("r", "b", null);
    collector.onHelperStart("r", "a", null);
    collector.onHelperEnd("r", "a", null, false);
    collector.onHelperEnd("r", "b", null, true);
    collector.onHelperEnd("r", "a", null, true);
    collector.onProcessEnd("r", "p", null, true);
    collector.finish();

    var root = collector.tree().orElseThrow();
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
  void perRunInstancesAreIsolated() {
    var collector1 = new ExecutionTreeCollector();
    collector1.start();
    collector1.onProcessStart("r1", "p", null);
    collector1.onHelperStart("r1", "a", null);
    collector1.onHelperStart("r1", "b", null);
    collector1.onHelperStart("r1", "a", null);
    collector1.onHelperEnd("r1", "a", null, false);
    collector1.onHelperEnd("r1", "b", null, true);
    collector1.onHelperEnd("r1", "a", null, true);
    collector1.onProcessEnd("r1", "p", null, true);
    collector1.finish();

    var collector2 = new ExecutionTreeCollector();
    collector2.start();
    collector2.onProcessStart("r2", "p", null);
    collector2.onHelperStart("r2", "a", null);
    collector2.onHelperStart("r2", "b", null);
    collector2.onHelperEnd("r2", "b", null, true);
    collector2.onHelperEnd("r2", "a", null, true);
    collector2.onProcessEnd("r2", "p", null, true);
    collector2.finish();

    var r1 = collector1.tree().orElseThrow();
    assertThat(r1.children().get(0).children().get(0).children().get(0).name())
            .isEqualTo("<truncated>");

    var r2 = collector2.tree().orElseThrow();
    assertThat(r2.children()).hasSize(1);
    assertThat(r2.children().get(0).children()).hasSize(1);
    assertThat(r2.children().get(0).children().get(0).name()).isEqualTo("b");
  }

  @Test
  void finishClearsCycleSet() {
    var collector = new ExecutionTreeCollector();
    collector.start();
    collector.onProcessStart("r1", "p", null);
    collector.onHelperStart("r1", "a", null);
    collector.onHelperStart("r1", "a", null);
    collector.onHelperEnd("r1", "a", null, false);
    collector.onHelperEnd("r1", "a", null, true);
    collector.onProcessEnd("r1", "p", null, true);
    collector.finish();

    collector.start();
    collector.onProcessStart("r1", "p", null);
    collector.onHelperStart("r1", "a", null);
    collector.onHelperEnd("r1", "a", null, true);
    collector.onProcessEnd("r1", "p", null, true);
    collector.finish();

    var root = collector.tree().orElseThrow();
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).name()).isEqualTo("a");
    assertThat(root.children().get(0).children()).isEmpty();
  }

  @Test
  void maxDepthOneTruncatesEveryChild() {
    var collector = new ExecutionTreeCollector(1);
    collector.start();
    collector.onProcessStart("r", "p", null);
    collector.onHelperStart("r", "h", null);
    collector.onHelperEnd("r", "h", null, false);
    collector.onProcessEnd("r", "p", null, true);
    collector.finish();

    var root = collector.tree().orElseThrow();
    assertThat(root.name()).isEqualTo("p");
    assertThat(root.children()).hasSize(1);
    var truncated = root.children().get(0);
    assertThat(truncated.name()).isEqualTo("<truncated>");
    assertThat(truncated.kind()).isEqualTo(CallKind.HELPER);
    assertThat(truncated.success()).isFalse();
  }
}
