package cbs.nova.starter.helper.model;

import java.math.BigDecimal;

public record SumValuesOut(BigDecimal sum) {

  public BigDecimal result() {
    return sum;
  }
}
