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
  private final CompensationRegistry compensationRegistry = new CompensationRegistry();

  @Test
  void singleLevelProducesLeafRoot() {
    var collector = new ExecutionTreeCollector();
    collector.start();
    collector.onProcessStart("r1", "p", "in");
    collector.onProcessEnd("r1", "p", "out", true);
    collector.finish();

    var tree = collector.tree();
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
    collector.start();
    collector.onProcessStart("r2", "p", "in");
    collector.onHelperStart("r2", "h", "hi");
    collector.onTransactionStart("r2", "t", "ti");
    collector.onTransactionEnd("r2", "t", "to", true);
    collector.onHelperEnd("r2", "h", "ho", true);
    collector.onProcessEnd("r2", "p", "po", true);
    collector.finish();

    var tree = collector.tree();
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
  void perRunInstancesAreIsolated() {
    var collectorA = new ExecutionTreeCollector();
    collectorA.start();
    collectorA.onProcessStart("rA", "pA", null);
    collectorA.onProcessEnd("rA", "pA", "outA", true);
    collectorA.finish();

    var collectorB = new ExecutionTreeCollector();
    collectorB.start();
    collectorB.onProcessStart("rB", "pB", null);
    collectorB.onHelperStart("rB", "hB", null);
    collectorB.onHelperEnd("rB", "hB", null, true);
    collectorB.onProcessEnd("rB", "pB", "outB", true);
    collectorB.finish();

    var a = collectorA.tree().orElseThrow();
    assertThat(a.name()).isEqualTo("pA");
    assertThat(a.children()).isEmpty();

    var b = collectorB.tree().orElseThrow();
    assertThat(b.name()).isEqualTo("pB");
    assertThat(b.children()).hasSize(1);
    assertThat(b.children().get(0).kind()).isEqualTo(CallKind.HELPER);
  }

  @Test
  void externalCallsAttachToActiveFrame() {
    var collector = new ExecutionTreeCollector();
    collector.start();
    collector.onProcessStart("r3", "p", null);
    collector.onHelperStart("r3", "h", null);
    collector.attachExternalCall(Map.of("type", "database", "sql", "SELECT 1"));
    collector.onHelperEnd("r3", "h", null, true);
    collector.onProcessEnd("r3", "p", null, true);
    collector.finish();

    var root = collector.tree().orElseThrow();
    var helper = root.children().get(0);
    assertThat(helper.externalCalls()).hasSize(1);
    assertThat(helper.externalCalls().get(0)).containsEntry("type", "database")
            .containsEntry("sql", "SELECT 1");
    assertThat(root.externalCalls()).isEmpty();
  }

  @Test
  void treeForUnusedCollectorIsEmpty() {
    var collector = new ExecutionTreeCollector();
    assertThat(collector.tree()).isEmpty();
  }

  @Test
  void eventsWithoutStartAreIgnored() {
    var collector = new ExecutionTreeCollector();
    collector.onProcessStart("unknown", "p", null);
    collector.onProcessEnd("unknown", "p", null, true);
    assertThat(collector.tree()).isEmpty();
  }

  @Test
  void startClearsPriorTree() {
    var collector = new ExecutionTreeCollector();
    collector.start();
    collector.onProcessStart("r4", "p1", null);
    collector.onProcessEnd("r4", "p1", null, true);
    collector.finish();
    assertThat(collector.tree()).isPresent();

    collector.start();
    assertThat(collector.tree()).isEmpty();
    collector.onProcessStart("r4", "p2", null);
    collector.onProcessEnd("r4", "p2", null, true);
    collector.finish();
    assertThat(collector.tree()).map(CallNode::name).contains("p2");
  }

  @Test
  void transactionRunnerForwardsEventsToListener() {
    var collector = new ExecutionTreeCollector();
    collector.start();
    var ctx = contextFactory.of("in", ExecutionMode.PREVIEW, "rt").withExecutionListener(collector);
    var tx = Dsl.transaction("T")
            .execute(c -> Result.success("done"))
            .build();
    var runner = new DefaultTransactionRunner(contextFactory, compensationRegistry);
    runner.run(tx, ctx);
    collector.finish();

    var root = collector.tree().orElseThrow();
    assertThat(root.kind()).isEqualTo(CallKind.TRANSACTION);
    assertThat(root.name()).isEqualTo("T");
    assertThat(root.success()).isTrue();
    assertThat(root.output()).isEqualTo("done");
  }

  @Test
  void helperRunnerForwardsEventsToListener() {
    var collector = new ExecutionTreeCollector();
    collector.start();
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "rh").withExecutionListener(collector);
    var registry = new DefaultHelperRegistry();
    registry.registerHelper("echo", new EchoHelper());
    var runner = new DefaultHelperRunner(contextFactory);
    runner.runHelper("echo", ctx, registry);
    collector.finish();

    var root = collector.tree().orElseThrow();
    assertThat(root.kind()).isEqualTo(CallKind.HELPER);
    assertThat(root.name()).isEqualTo("echo");
    assertThat(root.success()).isTrue();
    assertThat(root.output()).isEqualTo("ECHO");
  }

  @Test
  void functionRunnerForwardsEventsToListener() {
    var collector = new ExecutionTreeCollector();
    collector.start();
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "rf").withExecutionListener(collector);
    var registry = new DefaultHelperRegistry();
    registry.registerFunction(Dsl.function("fn")
            .execute(c -> Result.success("FN_OUT"))
            .build());
    var runner = new DefaultHelperRunner(contextFactory);
    runner.runFunction("fn", ctx, registry);
    collector.finish();

    var root = collector.tree().orElseThrow();
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
