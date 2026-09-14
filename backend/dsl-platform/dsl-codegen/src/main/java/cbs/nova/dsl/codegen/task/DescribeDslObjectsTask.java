package cbs.nova.dsl.codegen.task;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.config.DescriptorFactory;
import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.transaction.TransactionDslObject;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.concurrent.Callable;

@RequiredArgsConstructor
public final class DescribeDslObjectsTask implements CompileTask {

  private final DescriptorFactory descriptorFactory;

  @Override
  public String name() {
    return "describe";
  }

  @Override
  public CompileContext run(CompileContext context) throws IOException {
    var objects = context.objects();
    return context.toBuilder()
            .processes(VirtualThreads.runAll(objects.stream()
                    .filter(obj -> obj.type() == DslType.PROCESS)
                    .<Callable<ProcessDescriptor>>map(
                            obj -> () -> descriptorFactory.fromProcess((ProcessDslObject) obj))
                    .toList()))
            .transactions(VirtualThreads.runAll(objects.stream()
                    .filter(obj -> obj.type() == DslType.TRANSACTION)
                    .<Callable<TransactionDescriptor>>map(
                            obj -> () -> descriptorFactory
                                    .fromTransaction((TransactionDslObject) obj))
                    .toList()))
            .functions(VirtualThreads.runAll(objects.stream()
                    .filter(obj -> obj.type() == DslType.FUNCTION)
                    .<Callable<FunctionDescriptor>>map(
                            obj -> () -> descriptorFactory.fromFunction((FunctionDslObject) obj))
                    .toList()))
            .build();
  }
}
