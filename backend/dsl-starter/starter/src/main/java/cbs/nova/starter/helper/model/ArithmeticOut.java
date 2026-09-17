package cbs.nova.starter.helper.model;

import java.math.BigDecimal;

/**
 * Output for the built-in {@code arithmetic} helper.
 *
 * <p>
 * The concrete type of {@code result} depends on the operation: legacy arithmetic operations return
 * {@link BigDecimal}, math aggregation modes return {@link Double}, and {@code floor} /
 * {@code ceil} return {@link Long}.
 */
public record ArithmeticOut(Object result) {

  public BigDecimal sum() {
    return (BigDecimal) result;
  }
}
