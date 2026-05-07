package cbs.dsl.builder;

import cbs.dsl.api.DslDefinitionCollector;
import cbs.dsl.api.DslObject;
import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.LockDefinition;
import cbs.dsl.api.MassOperationTypes.MassOperationInput;
import cbs.dsl.api.MassOperationTypes.MassOperationOutput;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.SignalTypes;
import cbs.dsl.api.SourceDefinition;
import cbs.dsl.api.TriggerDefinition;
import cbs.dsl.api.context.MassOperationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Builder for creating mass operation objects from DSL files. */
public class MassOperationBuilder {

  private final String code;
  private String category = "DEFAULT";
  private final List<ParameterDefinition> parameters = new ArrayList<>();
  private final List<TriggerDefinition> triggers = new ArrayList<>();
  private SourceDefinition source;
  private LockDefinition lock;
  private Consumer<MassOperationContext> contextBlock = ctx -> {};
  private Consumer<MassOperationContext> itemBlock = ctx -> {};
  private Consumer<SignalTypes.Signal> onPartial;
  private Consumer<SignalTypes.Signal> onCompleted;
  private BiConsumer<MassOperationContext, Throwable> finishBlock = (ctx, ex) -> {};

  MassOperationBuilder(String code) {
    this.code = code;
  }

  public MassOperationBuilder category(String category) {
    this.category = category;
    return this;
  }

  public MassOperationBuilder parameters(Consumer<ParametersBuilder> block) {
    ParametersBuilder builder = new ParametersBuilder();
    block.accept(builder);
    this.parameters.addAll(builder.build());
    return this;
  }

  public MassOperationBuilder trigger(TriggerDefinition trigger) {
    this.triggers.add(trigger);
    return this;
  }

  public MassOperationBuilder source(SourceDefinition source) {
    this.source = source;
    return this;
  }

  public MassOperationBuilder lock(LockDefinition lock) {
    this.lock = lock;
    return this;
  }

  public MassOperationBuilder context(Consumer<MassOperationContext> block) {
    this.contextBlock = block;
    return this;
  }

  public MassOperationBuilder item(Consumer<MassOperationContext> block) {
    this.itemBlock = block;
    return this;
  }

  public MassOperationBuilder onPartial(Consumer<SignalTypes.Signal> block) {
    this.onPartial = block;
    return this;
  }

  public MassOperationBuilder onCompleted(Consumer<SignalTypes.Signal> block) {
    this.onCompleted = block;
    return this;
  }

  public MassOperationBuilder finish(BiConsumer<MassOperationContext, Throwable> block) {
    this.finishBlock = block;
    return this;
  }

  public String getCode() {
    return code;
  }

  public String getCategory() {
    return category;
  }

  public List<ParameterDefinition> getParameters() {
    return Collections.unmodifiableList(new ArrayList<>(parameters));
  }

  public List<TriggerDefinition> getTriggers() {
    return Collections.unmodifiableList(new ArrayList<>(triggers));
  }

  public SourceDefinition getSource() {
    return source;
  }

  public LockDefinition getLock() {
    return lock;
  }

  public Consumer<MassOperationContext> getContextBlock() {
    return contextBlock;
  }

  public Consumer<MassOperationContext> getItemBlock() {
    return itemBlock;
  }

  public Consumer<SignalTypes.Signal> getOnPartial() {
    return onPartial;
  }

  public Consumer<SignalTypes.Signal> getOnCompleted() {
    return onCompleted;
  }

  public DslObject build() {
    DslObject obj = new MassOperationDslObject(
        code,
        category,
        Collections.unmodifiableList(new ArrayList<>(parameters)),
        Collections.unmodifiableList(new ArrayList<>(triggers)),
        source,
        lock,
        contextBlock,
        itemBlock,
        onPartial,
        onCompleted);
    DslDefinitionCollector.register(obj);
    return obj;
  }
}
