package cbs.dsl.builder;

import cbs.dsl.api.ConditionDefinition;
import cbs.dsl.api.ConditionTypes.ConditionInput;
import cbs.dsl.api.ConditionTypes.ConditionOutput;
import cbs.dsl.api.DslDefinitionCollector;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.TransactionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Builder for creating inline {@link ConditionDefinition} instances from DSL files.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * ConditionDefinition condition = ConditionDsl.condition("ACCOUNT_ACTIVE")
 *     .requiredParam("accountCode")
 *     .evaluate(ctx -> {
 *         var account = ctx.resolve(AccountService.class).findByCode(ctx.get("accountCode"));
 *         return new ConditionOutput(account != null && account.isActive());
 *     })
 *     .build();
 * }</pre>
 */
public class ConditionBuilder {

  private final String code;
  private final List<ParameterDefinition> parameters = new ArrayList<>();
  private Function<TransactionContext, ConditionOutput> evaluateBlock;

  ConditionBuilder(String code) {
    this.code = code;
  }

  public ConditionBuilder requiredParam(String name) {
    this.parameters.add(new ParameterDefinition(name, true));
    return this;
  }

  public ConditionBuilder optionalParam(String name) {
    this.parameters.add(new ParameterDefinition(name, false));
    return this;
  }

  public ConditionBuilder evaluate(Function<TransactionContext, ConditionOutput> block) {
    this.evaluateBlock = block;
    return this;
  }

  public ConditionDefinition build() {
    List<ParameterDefinition> params = Collections.unmodifiableList(new ArrayList<>(parameters));
    ConditionDefinition def = new ConditionDefinition() {
      @Override
      public String getCode() {
        return code;
      }

      @Override
      public List<ParameterDefinition> getParameters() {
        return params;
      }

      @Override
      public Predicate<TransactionContext> getPredicate() {
        return ctx -> {
          if (evaluateBlock == null) {
            return false;
          }
          return evaluateBlock.apply(ctx).result();
        };
      }

      @Override
      public ConditionOutput evaluate(ConditionInput input) {
        if (evaluateBlock == null) {
          return new ConditionOutput(false);
        }
        TransactionContext ctx = new TransactionContext(
            input.eventCode(),
            input.eventNumber(),
            null,
            "dev",
            input.params() != null ? input.params() : Collections.emptyMap(),
            false);
        return evaluateBlock.apply(ctx);
      }
    };
    DslDefinitionCollector.register(def);
    return def;
  }
}
