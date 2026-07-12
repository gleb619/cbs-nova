package cbs.nova.dsl.config;

import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.transaction.TransactionDslObject;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class DescriptorFactory {

  public ProcessDescriptor fromProcess(@NonNull ProcessDslObject obj) {
    return new ProcessDescriptor(
            obj.name(),
            obj.version(),
            obj.taskQueue(),
            obj.inputType(),
            obj.outputType(),
            obj.compensationLogic() != null,
            List.of());
  }

  public TransactionDescriptor fromTransaction(@NonNull TransactionDslObject obj) {
    return new TransactionDescriptor(
            obj.name(),
            obj.version(),
            obj.taskQueue(),
            obj.inputType(),
            obj.outputType(),
            obj.compensationLogic() != null,
            List.of(),
            obj.startToCloseTimeout(),
            obj.retryPolicy(),
            obj.heartbeatTimeout());
  }

  public FunctionDescriptor fromFunction(@NonNull FunctionDslObject obj) {
    return new FunctionDescriptor(obj.name(), null, null);
  }
}
