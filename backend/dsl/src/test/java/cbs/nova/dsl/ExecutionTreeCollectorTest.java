package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.runner.DefaultHelperRunner;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ExecutionTreeCollectorTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();
  private final CompensationRegistry compensationRegistry = new CompensationRegistry();

  @Test
  void singleLevelProducesLeafRoot() {
    var collector = new ExecutionTreeCollector();
    collector.startRun("r1");
    collector.onProcessStart("r1", "p", "in");
    collector.onProcessEnd("r1", "p", "out", true);
    collector.finishRun("r1");

    var tree = collector.tree("r1");
    assertThat(tree).isPresent();
    var root = tree.get();
    assertThat(root.name()).isEqualTo("p");
    assertThat(root.kind()).isEqualTo(CallKind.PROCESS);
    assertThat(root.input()).isEqualTo("in");
    assertThat(root.output()).isEqualTo("out");
    assertThat(root.success()).isTrue();
    assertThat(root.children()).isEmpty();
    assertThat(root.externalCalls()).isEmpty();
  }

  @Test
  void nestedProcessHelperTransactionBuildsTree() {
    var collector = new ExecutionTreeCollector();
    collector.startRun("r2");
    collector.onProcessStart("r2", "p", "in");
    collector.onHelperStart("r2", "h", "hi");
    collector.onTransactionStart("r2", "t", "ti");
    collector.onTransactionEnd("r2", "t", "to", true);
    collector.onHelperEnd("r2", "h", "ho", true);
    collector.onProcessEnd("r2", "p", "po", true);
    collector.finishRun("r2");

    var tree = collector.tree("r2");
    assertThat(tree).isPresent();
    var root = tree.get();
    assertThat(root.kind()).isEqualTo(CallKind.PROCESS);
    assertThat(root.children()).hasSize(1);

    var helper = root.children().get(0);
    assertThat(helper.kind()).isEqualTo(CallKind.HELPER);
    assertThat(helper.output()).isEqualTo("ho");
    assertThat(helper.children()).hasSize(1);

    var tx = helper.children().get(0);
    assertThat(tx.kind()).isEqualTo(CallKind.TRANSACTION);
    assertThat(tx.success()).isTrue();
    assertThat(tx.output()).isEqualTo("to");
    assertThat(tx.children()).isEmpty();
  }

  @Test
  void runIdsAreIsolated() {
    var collector = new ExecutionTreeCollector();
    collector.startRun("rA");
    collector.startRun("rB");

    collector.onProcessStart("rA", "pA", null);
    collector.onProcessEnd("rA", "pA", "outA", true);
    collector.finishRun("rA");

    collector.onProcessStart("rB", "pB", null);
    collector.onHelperStart("rB", "hB", null);
    collector.onHelperEnd("rB", "hB", null, true);
    collector.onProcessEnd("rB", "pB", "outB", true);
    collector.finishRun("rB");

    var a = collector.tree("rA").orElseThrow();
    assertThat(a.name()).isEqualTo("pA");
    assertThat(a.children()).isEmpty();

    var b = collector.tree("rB").orElseThrow();
    assertThat(b.name()).isEqualTo("pB");
    assertThat(b.children()).hasSize(1);
    assertThat(b.children().get(0).kind()).isEqualTo(CallKind.HELPER);
  }

  @Test
  void externalCallsAttachToActiveFrame() {
    var collector = new ExecutionTreeCollector();
    collector.startRun("r3");
    collector.onProcessStart("r3", "p", null);
    collector.onHelperStart("r3", "h", null);
    collector.attachExternalCall("r3", Map.of("type", "database", "sql", "SELECT 1"));
    collector.onHelperEnd("r3", "h", null, true);
    collector.onProcessEnd("r3", "p", null, true);
    collector.finishRun("r3");

    var root = collector.tree("r3").orElseThrow();
    var helper = root.children().get(0);
    assertThat(helper.externalCalls()).hasSize(1);
    assertThat(helper.externalCalls().get(0)).containsEntry("type", "database")
            .containsEntry("sql", "SELECT 1");
    assertThat(root.externalCalls()).isEmpty();
  }

  @Test
  void treeForUnknownRunIdIsEmpty() {
    var collector = new ExecutionTreeCollector();
    assertThat(collector.tree("ghost")).isEmpty();
  }

  @Test
  void eventsWithoutStartRunAreIgnored() {
    var collector = new ExecutionTreeCollector();
    collector.onProcessStart("unknown", "p", null);
    collector.onProcessEnd("unknown", "p", null, true);
    assertThat(collector.tree("unknown")).isEmpty();
  }

  @Test
  void startRunClearsPriorTree() {
    var collector = new ExecutionTreeCollector();
    collector.startRun("r4");
    collector.onProcessStart("r4", "p1", null);
    collector.onProcessEnd("r4", "p1", null, true);
    collector.finishRun("r4");
    assertThat(collector.tree("r4")).isPresent();

    collector.startRun("r4");
    assertThat(collector.tree("r4")).isEmpty();
    collector.onProcessStart("r4", "p2", null);
    collector.onProcessEnd("r4", "p2", null, true);
    collector.finishRun("r4");
    assertThat(collector.tree("r4")).map(CallNode::name).contains("p2");
  }

  @Test
  void transactionRunnerForwardsEventsToListener() {
    var collector = new ExecutionTreeCollector();
    collector.startRun("rt");
    var ctx = contextFactory.of("in", ExecutionMode.PREVIEW, "rt").withExecutionListener(collector);
    var tx = Dsl.transaction("T")
            .execute(c -> Result.success("done"))
            .build();
    var runner = new DefaultTransactionRunner(traceCollector, contextFactory, compensationRegistry);
    runner.run(tx, ctx);
    collector.finishRun("rt");

    var root = collector.tree("rt").orElseThrow();
    assertThat(root.kind()).isEqualTo(CallKind.TRANSACTION);
    assertThat(root.name()).isEqualTo("T");
    assertThat(root.success()).isTrue();
    assertThat(root.output()).isEqualTo("done");
  }

  @Test
  void helperRunnerForwardsEventsToListener() {
    var collector = new ExecutionTreeCollector();
    collector.startRun("rh");
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "rh").withExecutionListener(collector);
    var registry = new DefaultHelperRegistry();
    registry.registerHelper("echo", new EchoHelper());
    var runner = new DefaultHelperRunner(traceCollector, contextFactory);
    runner.runHelper("echo", ctx, registry);
    collector.finishRun("rh");

    var root = collector.tree("rh").orElseThrow();
    assertThat(root.kind()).isEqualTo(CallKind.HELPER);
    assertThat(root.name()).isEqualTo("echo");
    assertThat(root.success()).isTrue();
    assertThat(root.output()).isEqualTo("ECHO");
  }

  @Test
  void functionRunnerForwardsEventsToListener() {
    var collector = new ExecutionTreeCollector();
    collector.startRun("rf");
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "rf").withExecutionListener(collector);
    var registry = new DefaultHelperRegistry();
    registry.registerFunction(Dsl.function("fn")
            .execute(c -> Result.success("FN_OUT"))
            .build());
    var runner = new DefaultHelperRunner(traceCollector, contextFactory);
    runner.runFunction("fn", ctx, registry);
    collector.finishRun("rf");

    var root = collector.tree("rf").orElseThrow();
    assertThat(root.kind()).isEqualTo(CallKind.FUNCTION);
    assertThat(root.name()).isEqualTo("fn");
    assertThat(root.output()).isEqualTo("FN_OUT");
  }

  @Test
  void leafFactoryReturnsEmptyContainers() {
    var leaf = CallNode.leaf("p", CallKind.PROCESS, "in", "out", true);
    assertThat(leaf.children()).isEmpty();
    assertThat(leaf.externalCalls()).isEmpty();
    assertThat(leaf.name()).isEqualTo("p");
  }

  @Test
  void nodeFactoryPreservesCollections() {
    var children = List.of(CallNode.leaf("c", CallKind.HELPER, null, null, true));
    var ext = List.<Map<String, Object>>of(Map.of("kind", "http"));
    var node = CallNode.node("p", CallKind.PROCESS, "in", "out", true, children, ext);
    assertThat(node.children()).hasSize(1);
    assertThat(node.externalCalls()).hasSize(1);
  }

  private static final class EchoHelper implements Executable<String, String> {

    @Override
    public @NonNull Result<String> execute(@NonNull Context<String> ctx) {
      return Result.success("ECHO");
    }
  }
}
