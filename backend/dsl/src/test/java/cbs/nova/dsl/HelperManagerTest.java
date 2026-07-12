package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.registry.HelperRegistry;
import cbs.nova.dsl.runner.HelperRunner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class HelperManagerTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @Test
  void registerHelperDelegatesToRegistry() {
    var registry = new StubHelperRegistry();
    var runner = new RecordingHelperRunner();
    var manager = new HelperManager(registry, runner);
    Executable<String, String> helper = ctx -> Result.success("ignored");

    manager.registerHelper("greet", helper);

    assertThat(registry.registeredHelpers).containsExactly(Map.entry("greet", helper));
  }

  @Test
  void registerFunctionDelegatesToRegistry() {
    var registry = new StubHelperRegistry();
    var runner = new RecordingHelperRunner();
    var manager = new HelperManager(registry, runner);
    var fn = Dsl.function("greetFn").execute(ctx -> Result.success("ignored")).build();

    manager.registerFunction(fn);

    assertThat(registry.registeredFunctions).containsExactly(fn);
  }

  @Test
  void executeHelperDelegatesToRunnerAndReturnsItsResult() {
    var registry = new StubHelperRegistry();
    var expected = Result.success("runner-helper-result");
    var runner = new RecordingHelperRunner(expected, Result.success("unused-fn"));
    var manager = new HelperManager(registry, runner);
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-1");

    var result = manager.executeHelper("greet", ctx);

    assertThat(result).isSameAs(expected);
    assertThat(runner.helperNames).containsExactly("greet");
    assertThat(runner.helperContexts).containsExactly(ctx);
    assertThat(runner.helperRegistries).containsExactly(registry);
    assertThat(runner.functionNames).isEmpty();
  }

  @Test
  void executeFunctionDelegatesToRunnerAndReturnsItsResult() {
    var registry = new StubHelperRegistry();
    var expected = Result.success("runner-function-result");
    var runner = new RecordingHelperRunner(Result.success("unused-helper"), expected);
    var manager = new HelperManager(registry, runner);
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-2");

    var result = manager.executeFunction("greetFn", ctx);

    assertThat(result).isSameAs(expected);
    assertThat(runner.functionNames).containsExactly("greetFn");
    assertThat(runner.functionContexts).containsExactly(ctx);
    assertThat(runner.functionRegistries).containsExactly(registry);
    assertThat(runner.helperNames).isEmpty();
  }

  @Test
  void containsDelegatesToRegistry() {
    var helper = (Executable<String, String>) ctx -> Result.success("ignored");
    var fn = Dsl.function("fn").execute(ctx -> Result.success("ignored")).build();
    var registry = new StubHelperRegistry();
    registry.registerHelper("greet", helper);
    registry.registerFunction(fn);
    var manager = new HelperManager(registry, new RecordingHelperRunner());

    assertThat(manager.contains("greet")).isTrue();
    assertThat(manager.contains("fn")).isTrue();
    assertThat(manager.contains("missing")).isFalse();
    assertThat(registry.containsLookups)
            .containsExactly("greet", "fn", "missing");
  }

  @Test
  void findHelperAndFindFunctionDelegateToRegistry() {
    var helper = (Executable<String, String>) ctx -> Result.success("ignored");
    var fn = Dsl.function("fn").execute(ctx -> Result.success("ignored")).build();
    var registry = new StubHelperRegistry();
    registry.registerHelper("greet", helper);
    registry.registerFunction(fn);
    var manager = new HelperManager(registry, new RecordingHelperRunner());

    assertThat(manager.findHelper("greet")).contains(helper);
    assertThat(manager.findHelper("missing")).isEmpty();
    assertThat(manager.findFunction("fn")).contains(fn);
    assertThat(manager.findFunction("missing")).isEmpty();
    assertThat(registry.findHelperLookups)
            .containsExactly("greet", "missing");
    assertThat(registry.findFunctionLookups)
            .containsExactly("fn", "missing");
  }

  @Test
  void namesReturnsSortedViewOfRegistryAllNames() {
    var helper = (Executable<String, String>) ctx -> Result.success("ignored");
    var fnA = Dsl.function("aFn").execute(ctx -> Result.success("ignored")).build();
    var fnZ = Dsl.function("zFn").execute(ctx -> Result.success("ignored")).build();
    var registry = new StubHelperRegistry();
    registry.registerHelper("mHelper", helper);
    registry.registerFunction(fnZ);
    registry.registerFunction(fnA);
    var manager = new HelperManager(registry, new RecordingHelperRunner());

    var firstCall = manager.names();
    var secondCall = manager.names();

    assertThat(firstCall).containsExactly("aFn", "mHelper", "zFn");
    assertThat(secondCall).containsExactly("aFn", "mHelper", "zFn");
    assertThat(registry.allNamesCalls).isEqualTo(2);
  }

  private static final class StubHelperRegistry implements HelperRegistry {
    private final List<Map.Entry<String, Executable<?, ?>>> registeredHelpers = new ArrayList<>();
    private final List<FunctionDslObject> registeredFunctions = new ArrayList<>();
    private final java.util.LinkedHashMap<String, Executable<?, ?>> helpers = new java.util.LinkedHashMap<>();
    private final List<FunctionDslObject> functions = new ArrayList<>();
    private final List<String> findHelperLookups = new ArrayList<>();
    private final List<String> findFunctionLookups = new ArrayList<>();
    private final List<String> containsLookups = new ArrayList<>();
    private int allNamesCalls;

    @Override
    public void registerHelper(String name, Executable<?, ?> helper) {
      registeredHelpers.add(Map.entry(name, helper));
      helpers.put(name, helper);
    }

    @Override
    public void registerFunction(FunctionDslObject function) {
      registeredFunctions.add(function);
      functions.add(function);
    }

    @Override
    public Optional<Executable<?, ?>> findHelper(String name) {
      findHelperLookups.add(name);
      return Optional.ofNullable(helpers.get(name));
    }

    @Override
    public Optional<FunctionDslObject> findFunction(String name) {
      findFunctionLookups.add(name);
      return functions.stream()
              .filter(fn -> fn.name().equals(name))
              .findFirst();
    }

    @Override
    public boolean containsName(String name) {
      containsLookups.add(name);
      return helpers.containsKey(name)
              || functions.stream().anyMatch(fn -> fn.name().equals(name));
    }

    @Override
    public Collection<String> allNames() {
      allNamesCalls++;
      List<String> names = new ArrayList<>(helpers.keySet());
      functions.forEach(fn -> names.add(fn.name()));
      return names;
    }
  }

  private static final class RecordingHelperRunner implements HelperRunner {

    private final Result<?> helperResult;
    private final Result<?> functionResult;
    private final List<String> helperNames = new ArrayList<>();
    private final List<Context<?>> helperContexts = new ArrayList<>();
    private final List<HelperRegistry> helperRegistries = new ArrayList<>();
    private final List<String> functionNames = new ArrayList<>();
    private final List<Context<?>> functionContexts = new ArrayList<>();
    private final List<HelperRegistry> functionRegistries = new ArrayList<>();

    private RecordingHelperRunner() {
      this(Result.success("unused-helper"), Result.success("unused-function"));
    }

    private RecordingHelperRunner(Result<?> helperResult, Result<?> functionResult) {
      this.helperResult = helperResult;
      this.functionResult = functionResult;
    }

    @Override
    public Result<?> runHelper(String name, Context<?> ctx, HelperRegistry registry) {
      helperNames.add(name);
      helperContexts.add(ctx);
      helperRegistries.add(registry);
      return helperResult;
    }

    @Override
    public Result<?> runFunction(String name, Context<?> ctx, HelperRegistry registry) {
      functionNames.add(name);
      functionContexts.add(ctx);
      functionRegistries.add(registry);
      return functionResult;
    }
  }
}
