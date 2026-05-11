package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.Context;
import cbs.dsl.api.context.EventContext;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.api.context.TransactionsScope;
import java.util.function.BiConsumer;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@Getter
public class EventBuilder {

  private final String code;
  private String name;
  private final List<ParameterDefinition> parameters = new ArrayList<>();
  private Function<Context, Context> contextBlock = Context::copy;
  private Function<EventContext, EventContext> previewBlock;
  private Function<EventContext, EventContext> executeBlock;
  private Consumer<TransactionsScope> transactionsBlock;
  private BiConsumer<FinishContext, Throwable> finishBlock = (_, _) -> {};

  EventBuilder(String code) {
    this.code = code;
  }

  public EventBuilder name(String name) {
    this.name = name;
    return this;
  }

  public EventBuilder parameters(Consumer<ParametersBuilder> block) {
    ParametersBuilder builder = new ParametersBuilder();
    block.accept(builder);
    this.parameters.addAll(builder.build());
    return this;
  }

  public EventBuilder context(Function<Context, Context> block) {
    this.contextBlock = block;
    return this;
  }

  public EventBuilder preview(Function<EventContext, EventContext> block) {
    this.previewBlock = block;
    return this;
  }

  public EventBuilder execute(Function<EventContext, EventContext> block) {
    this.executeBlock = block;
    return this;
  }

  public List<ParameterDefinition> getParameters() {
    return Collections.unmodifiableList(new ArrayList<>(parameters));
  }

  public EventBuilder finish(BiConsumer<FinishContext, Throwable> block) {
    this.finishBlock = block;
    return this;
  }

  public DslObject build() {
    return EventDslObject.builder()
        .code(code)
        .parameters(Collections.unmodifiableList(new ArrayList<>(parameters)))
        .contextBlock(contextBlock)
        .previewBlock(previewBlock)
        .executeBlock(executeBlock)
        .build();
  }

}
