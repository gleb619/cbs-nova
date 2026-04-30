package cbs.dsl.builder;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.context.DisplayScope;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.api.context.TransactionsScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Builder for creating {@link EventDefinition} instances.
 *
 * <p>This is a convenience DSL — it describes wiring but does not execute anything.
 */
public class EventBuilder {

  private final String code;
  private final List<String> transactionCodes = new ArrayList<>();
  private Consumer<EnrichmentContext> contextBlock = ctx -> {};
  private Consumer<DisplayScope> displayBlock = scope -> {};
  private BiConsumer<FinishContext, Throwable> finishBlock = (ctx, ex) -> {};

  EventBuilder(String code) {
    this.code = code;
  }

  /**
   * Adds a transaction code to the event's execution sequence.
   *
   * @param transactionCode the transaction code to add
   * @return this builder for chaining
   */
  public EventBuilder transaction(String transactionCode) {
    this.transactionCodes.add(transactionCode);
    return this;
  }

  /**
   * Sets the context enrichment block.
   *
   * @param block the context block
   * @return this builder for chaining
   */
  public EventBuilder context(Consumer<EnrichmentContext> block) {
    this.contextBlock = block;
    return this;
  }

  /**
   * Sets the display block.
   *
   * @param block the display block
   * @return this builder for chaining
   */
  public EventBuilder display(Consumer<DisplayScope> block) {
    this.displayBlock = block;
    return this;
  }

  /**
   * Sets the finish block.
   *
   * @param block the finish block
   * @return this builder for chaining
   */
  public EventBuilder finish(BiConsumer<FinishContext, Throwable> block) {
    this.finishBlock = block;
    return this;
  }

  /**
   * Builds an immutable {@link EventDefinition}.
   *
   * @return the built event definition
   */
  public EventDefinition build() {
    List<String> txCodes = Collections.unmodifiableList(new ArrayList<>(transactionCodes));
    return new EventDefinition() {
      @Override
      public String getCode() {
        return code;
      }

      @Override
      public Consumer<EnrichmentContext> getContextBlock() {
        return contextBlock;
      }

      @Override
      public Consumer<DisplayScope> getDisplayBlock() {
        return displayBlock;
      }

      @Override
      public Consumer<TransactionsScope> getTransactionsBlock() {
        if (txCodes.isEmpty()) {
          return null;
        }
        return scope -> {
          for (String txCode : txCodes) {
            // In a generated workflow, this would be replaced by hardcoded activity calls.
            // The builder scope is for local/dev execution only.
          }
        };
      }

      @Override
      public BiConsumer<FinishContext, Throwable> getFinishBlock() {
        return finishBlock;
      }
    };
  }
}
