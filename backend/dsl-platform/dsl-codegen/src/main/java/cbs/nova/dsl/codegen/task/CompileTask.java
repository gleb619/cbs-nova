package cbs.nova.dsl.codegen.task;

/**
 * Single step of the DSL compilation pipeline. Implementations read pipeline state from and write
 * results to the shared {@link CompileContext}; the orchestrator runs tasks sequentially, while a
 * task may parallelize its own work internally.
 */
@FunctionalInterface
public interface CompileTask {

  default String name() {
    return this.getClass().getSimpleName();
  }

  CompileContext run(CompileContext context) throws Exception;
}
