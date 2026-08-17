package cbs.nova.starter.helpers.model;

import java.math.BigDecimal;

public record SumValuesOut(BigDecimal sum) {

  public BigDecimal result() {
    return sum;
  }
}
