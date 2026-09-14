package cbs.nova.dsl.config;

import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.model.MapOutput;
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
    return ProcessDescriptor.builder()
            .name(obj.name())
            .version(obj.version())
            .taskQueue(obj.taskQueue())
            .inputType(inputType)
            .outputType(outputType)
            .hasCompensation(obj.compensationLogic() != null)
            .helperRefs(List.of())
            .transactionRefs(List.of())
            .build();
  }

  public TransactionDescriptor fromTransaction(@NonNull TransactionDslObject obj) {
    var inputType = resolveInputType(obj.inputType(), obj.parameters());
    var outputType = resolveOutputType(obj.outputType(), obj.parameters());
    return TransactionDescriptor.builder()
            .name(obj.name())
            .version(obj.version())
            .taskQueue(obj.taskQueue())
            .inputType(inputType)
            .outputType(outputType)
            .hasCompensation(obj.compensationLogic() != null)
            .helperRefs(List.of())
            .startToCloseTimeout(obj.startToCloseTimeout())
            .retryPolicy(obj.retryPolicy())
            .heartbeatTimeout(obj.heartbeatTimeout())
            .build();
  }

  public FunctionDescriptor fromFunction(@NonNull FunctionDslObject obj) {
    var inputType = resolveInputType(obj.inputType(), obj.parameters());
    var outputType = resolveOutputType(obj.outputType(), obj.parameters());
    return FunctionDescriptor.builder()
            .name(obj.name())
            .inputType(inputType)
            .outputType(outputType)
            .build();
  }

  private Class<?> resolveInputType(Class<?> declaredType, List<?> parameters) {
    if (declaredType != null && declaredType != Void.class) {
      return declaredType;
    }
    return parameters != null && !parameters.isEmpty() ? MapInput.class : null;
  }

  private Class<?> resolveOutputType(Class<?> declaredType, List<?> parameters) {
    if (declaredType != null && declaredType != Void.class) {
      return declaredType;
    }
    return parameters != null && !parameters.isEmpty() ? MapOutput.class : null;
  }
}
