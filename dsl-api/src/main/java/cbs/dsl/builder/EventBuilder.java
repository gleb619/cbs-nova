package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.DisplayScope;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.api.context.TransactionsScope;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Getter
public class EventBuilder {

  private final String code;

  @Deprecated(forRemoval = true)
  private final List<String> transactionCodes = new ArrayList<>();

  private final List<ParameterDefinition> parameters = new ArrayList<>();
  private Consumer<EnrichmentContext> contextBlock = ctx -> {};

  @Deprecated(forRemoval = true)
  private Consumer<DisplayScope> displayBlock = scope -> {};

  private Consumer<TransactionsScope> transactionsBlock;
  private BiConsumer<FinishContext, Throwable> finishBlock = (_, _) -> {};

  EventBuilder(String code) {
    this.code = code;
  }

  public EventBuilder parameters(Consumer<ParametersBuilder> block) {
    ParametersBuilder builder = new ParametersBuilder();
    block.accept(builder);
    this.parameters.addAll(builder.build());
    return this;
  }

  @Deprecated(forRemoval = true)
  public EventBuilder transaction(String transactionCode) {
    this.transactionCodes.add(transactionCode);
    return this;
  }

  public EventBuilder context(Consumer<EnrichmentContext> block) {
    this.contextBlock = block;
    return this;
  }

  @Deprecated(forRemoval = true)
  public EventBuilder display(Consumer<DisplayScope> block) {
    this.displayBlock = block;
    return this;
  }

  @Deprecated(forRemoval = true)
  public EventBuilder transactions(Consumer<TransactionsScope> block) {
    this.transactionsBlock = block;
    return this;
  }

  public EventBuilder finish(BiConsumer<FinishContext, Throwable> block) {
    this.finishBlock = block;
    return this;
  }

  public DslObject build() {
    List<String> txCodes = Collections.unmodifiableList(new ArrayList<>(transactionCodes));
    List<ParameterDefinition> params = Collections.unmodifiableList(new ArrayList<>(parameters));
    Consumer<TransactionsScope> txBlock = transactionsBlock != null
        ? transactionsBlock
        : txCodes.isEmpty()
            ? null
            : scope -> {
              for (String txCode : txCodes) {}
            };

    return EventDslObject.builder()
        .code(code)
        .parameters(params)
        .contextBlock(contextBlock)
        .displayBlock(displayBlock)
        .transactionsBlock(txBlock)
        .transactionCodes(txCodes)
        .finishBlock(finishBlock)
        .build();
  }
}
