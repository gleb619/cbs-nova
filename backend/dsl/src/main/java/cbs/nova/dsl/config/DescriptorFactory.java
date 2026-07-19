package cbs.nova.dsl.config;

import cbs.nova.dsl.MapInput;
import cbs.nova.dsl.MapOutput;
import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.transaction.TransactionDslObject;
import java.util.List;
import org.jspecify.annotations.NonNull;

public final class DescriptorFactory {

  public ProcessDescriptor fromProcess(@NonNull ProcessDslObject obj) {
    var inputType = resolveInputType(obj.inputType(), obj.parameters());
    var outputType = resolveOutputType(obj.outputType(), obj.parameters());
    return new ProcessDescriptor(
            obj.name(),
            obj.version(),
            obj.taskQueue(),
            inputType,
            outputType,
            obj.compensationLogic() != null,
            List.of(),
            obj.transactionRefs() != null ? obj.transactionRefs() : List.of());
  }

  public TransactionDescriptor fromTransaction(@NonNull TransactionDslObject obj) {
    var inputType = resolveInputType(obj.inputType(), obj.parameters());
    var outputType = resolveOutputType(obj.outputType(), obj.parameters());
    return new TransactionDescriptor(
            obj.name(),
            obj.version(),
            obj.taskQueue(),
            inputType,
            outputType,
            obj.compensationLogic() != null,
            List.of(),
            obj.startToCloseTimeout(),
            obj.retryPolicy(),
            obj.heartbeatTimeout());
  }

  public FunctionDescriptor fromFunction(@NonNull FunctionDslObject obj) {
    return new FunctionDescriptor(obj.name(), null, null);
  }

  private static Class<?> resolveInputType(Class<?> declaredType, List<?> parameters) {
    if (declaredType != null) {
      return declaredType;
    }
    return parameters != null && !parameters.isEmpty() ? MapInput.class : null;
  }

  private static Class<?> resolveOutputType(Class<?> declaredType, List<?> parameters) {
    if (declaredType != null) {
      return declaredType;
    }
    return parameters != null && !parameters.isEmpty() ? MapOutput.class : null;
  }
}
