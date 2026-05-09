package cbs.dsl.builder;

import cbs.dsl.api.ContextTypes.ContextInput;
import cbs.dsl.api.ContextTypes.ContextOutput;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.StandardDslObject;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import lombok.Builder;

import java.util.List;
import java.util.function.Function;

@Builder(toBuilder = true)
public record TransactionDslObject(
    String code,
    String name,
    List<ParameterDefinition> parameters,
    Function<ContextInput, ContextOutput> contextBlock,
    Function<TransactionInput, TransactionOutput> previewBlock,
    Function<TransactionInput, TransactionOutput> executeBlock,
    Function<TransactionInput, TransactionOutput> rollbackBlock)
    implements StandardDslObject {}
