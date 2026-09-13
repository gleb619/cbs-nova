package cbs.nova.dsl.process;

import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.transaction.TransactionExecution;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN;

@Builder
public record ProcessDslObject(
    @NonNull String name,
    @NonNull String description,
    @NonNull String taskQueue,
    @NonNull String version,
    @NonNull Class<?> inputType,
    @NonNull Class<?> outputType,
    @NonNull List<ParameterDescriptor> parameters,
    @NonNull Function<ProcessContext<?>, Result<?>> executeLogic,
    @Nullable BiConsumer<CompensationContext<?>, List<TransactionExecution>> compensationLogic,
    @NonNull Function<ProcessContext<?>, Result<?>> previewLogic,
    @NonNull Function<ProcessContext<?>, Result<ExplainReport>> explainLogic,
    @NonNull DslDescriptor descriptor
) implements DslObject {

  public ProcessDslObject {
    if (description == null || description.isBlank()) {
      description = EMPTY_MARKDOWN;
    }
    if (inputType == null) {
      inputType = Void.class;
    }
    if (outputType == null) {
      outputType = Void.class;
    }
  }

  @Override
  public @NonNull DslType type() {
    return DslType.PROCESS;
  }

}
