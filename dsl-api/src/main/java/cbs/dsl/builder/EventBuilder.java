package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.DisplayScope;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.api.context.TransactionsScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EventBuilder {

  private final String code;
  private final List<String> transactionCodes = new ArrayList<>();
  private final List<ParameterDefinition> parameters = new ArrayList<>();
  private Consumer<EnrichmentContext> contextBlock = ctx -> {};
  private Consumer<DisplayScope> displayBlock = scope -> {};
  private Consumer<TransactionsScope> transactionsBlock;
  private BiConsumer<FinishContext, Throwable> finishBlock = (ctx, ex) -> {};

  EventBuilder(String code) {
    this.code = code;
  }

  public EventBuilder parameters(Consumer<ParametersBuilder> block) {
    ParametersBuilder builder = new ParametersBuilder();
    block.accept(builder);
    this.parameters.addAll(builder.build());
    return this;
  }

  public EventBuilder transaction(String transactionCode) {
    this.transactionCodes.add(transactionCode);
    return this;
  }

  public EventBuilder context(Consumer<EnrichmentContext> block) {
    this.contextBlock = block;
    return this;
  }

  public EventBuilder display(Consumer<DisplayScope> block) {
    this.displayBlock = block;
    return this;
  }

  public EventBuilder transactions(Consumer<TransactionsScope> block) {
    this.transactionsBlock = block;
    return this;
  }

  public EventBuilder finish(BiConsumer<FinishContext, Throwable> block) {
    this.finishBlock = block;
    return this;
  }

  public String getCode() {
    return code;
  }

  public Consumer<EnrichmentContext> context() {
    return contextBlock;
  }

  public Consumer<DisplayScope> display() {
    return displayBlock;
  }

  public Consumer<TransactionsScope> transactions() {
    return transactionsBlock != null
        ? transactionsBlock
        : transactionCodes.isEmpty()
            ? null
            : scope -> {
              for (String txCode : transactionCodes) {}
            };
  }

  public List<String> transactionCodes() {
    return Collections.unmodifiableList(new ArrayList<>(transactionCodes));
  }

  public BiConsumer<FinishContext, Throwable> finish() {
    return finishBlock;
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
