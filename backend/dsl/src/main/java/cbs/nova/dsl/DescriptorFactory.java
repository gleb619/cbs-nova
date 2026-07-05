package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;

public final class DescriptorFactory {

  private DescriptorFactory() {
  }

  public static @NonNull ProcessDescriptor fromProcess(@NonNull ProcessDslObject obj) {
    return new ProcessDescriptor(
            obj.name(),
            obj.version(),
            obj.taskQueue(),
            obj.inputType(),
            obj.outputType(),
            obj.compensationLogic() != null,
            List.of());
  }

  public static @NonNull TransactionDescriptor fromTransaction(@NonNull TransactionDslObject obj) {
    return new TransactionDescriptor(
            obj.name(),
            obj.version(),
            obj.taskQueue(),
            obj.inputType(),
            obj.outputType(),
            obj.compensationLogic() != null,
            List.of(),
            obj.startToCloseTimeout(),
            obj.retryPolicy());
  }

  public static @NonNull FunctionDescriptor fromFunction(@NonNull FunctionDslObject obj) {
    return new FunctionDescriptor(obj.name(), null, null);
  }
}
