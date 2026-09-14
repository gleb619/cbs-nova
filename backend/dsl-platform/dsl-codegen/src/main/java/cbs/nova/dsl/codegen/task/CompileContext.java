package cbs.nova.dsl.codegen.task;

import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.codegen.model.DslCompilerOptions;
import cbs.nova.dsl.codegen.model.GeneratedSource;
import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record CompileContext(
        DslCompilerOptions options,
        List<DslObject> objects,
        List<String> preprocessedSources,
        List<ProcessDescriptor> processes,
        List<TransactionDescriptor> transactions,
        List<FunctionDescriptor> functions,
        List<GeneratedSource> generatedSources,
        List<String> providerFqns) {

  public static CompileContext create(DslCompilerOptions options) {
    return CompileContext.builder()
            .options(options)
            .objects(List.of())
            .preprocessedSources(List.of())
            .processes(List.of())
            .transactions(List.of())
            .functions(List.of())
            .generatedSources(List.of())
            .providerFqns(List.of())
            .build();
  }
}
