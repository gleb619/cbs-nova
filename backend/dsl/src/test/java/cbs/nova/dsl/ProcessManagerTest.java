package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslEntityNotFoundException;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.process.ProcessManager;
import cbs.nova.dsl.process.ProcessRegistry;
import cbs.nova.dsl.process.ProcessRunner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

class ProcessManagerTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @Test
  void executeDispatchesRegisteredProcessToRunner() {
    var process = process("RegisteredProcess");
    var registry = new StubProcessRegistry(process);
    var expected = Result.success("runner-result");
    var runner = new RecordingProcessRunner(expected);
    var manager = new ProcessManager(registry, runner);
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-1");

    var result = manager.execute("RegisteredProcess", ctx);

    assertThat(result).isSameAs(expected);
    assertThat(runner.processes).containsExactly(process);
    assertThat(runner.contexts).containsExactly(ctx);
  }

  @Test
  void executeReturnsFailureForUnknownName() {
    var registry = new StubProcessRegistry();
    var runner = new RecordingProcessRunner(Result.success("unused"));
    var manager = new ProcessManager(registry, runner);
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-2");

    var result = manager.execute("MissingProcess", ctx);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(DslEntityNotFoundException.class);
    assertThat(result.cause().getMessage()).contains("Process not found: MissingProcess");
    assertThat(runner.processes).isEmpty();
    assertThat(runner.contexts).isEmpty();
  }

  @Test
  void containsFindAndNamesReflectRegistryFindAndAll() {
    var zProcess = process("ZProcess");
    var aProcess = process("AProcess");
    var registry = new StubProcessRegistry(zProcess, aProcess);
    var manager = new ProcessManager(registry,
            new RecordingProcessRunner(Result.success("unused")));

    assertThat(manager.contains("AProcess")).isTrue();
    assertThat(manager.contains("MissingProcess")).isFalse();
    assertThat(manager.find("ZProcess")).contains(zProcess);
    assertThat(manager.names()).containsExactly("AProcess", "ZProcess");
    assertThat(registry.findLookups).containsExactly("AProcess", "MissingProcess", "ZProcess");
    assertThat(registry.allCalls).isEqualTo(1);
  }

  private static ProcessDslObject process(String name) {
    return Dsl.process(name).input(String.class).output(String.class)
            .execute(ctx -> Result.success("result-" + name))
            .build();
  }

  private static final class StubProcessRegistry implements ProcessRegistry {

    private final List<ProcessDslObject> processes;
    private final List<String> findLookups = new ArrayList<>();
    private int allCalls;

    private StubProcessRegistry(ProcessDslObject... processes) {
      this.processes = new ArrayList<>(List.of(processes));
    }

    @Override
    public void register(ProcessDslObject process) {
      processes.add(process);
    }

    @Override
    public Optional<ProcessDslObject> find(String name) {
      findLookups.add(name);
      return processes.stream().filter(process -> process.name().equals(name)).findFirst();
    }

    @Override
    public Collection<ProcessDslObject> all() {
      allCalls++;
      return List.copyOf(processes);
    }
  }

  private static final class RecordingProcessRunner implements ProcessRunner {

    private final Result<?> result;
    private final List<ProcessDslObject> processes = new ArrayList<>();
    private final List<Context<?>> contexts = new ArrayList<>();

    private RecordingProcessRunner(Result<?> result) {
      this.result = result;
    }

    @Override
    public Result<?> run(ProcessDslObject process, Context<?> ctx) {
      processes.add(process);
      contexts.add(ctx);
      return result;
    }
  }
}
