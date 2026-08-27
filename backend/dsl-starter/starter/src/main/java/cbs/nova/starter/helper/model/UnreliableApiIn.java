package cbs.nova.starter.helper.model;

import org.jspecify.annotations.Nullable;

public record UnreliableApiIn(
        String operationId,
        int failCount,
        boolean jitter,
        @Nullable String reason,
        @Nullable String pattern) {

  public UnreliableApiIn(String operationId, int failCount, boolean jitter, String reason) {
    this(operationId, failCount, jitter, reason, null);
  }

  public UnreliableApiFailurePattern effectivePattern() {
    if (pattern == null || pattern.isBlank()) {
      return UnreliableApiFailurePattern.CONSECUTIVE;
    }
    try {
      return UnreliableApiFailurePattern.valueOf(pattern.toUpperCase());
    } catch (IllegalArgumentException e) {
      return UnreliableApiFailurePattern.CONSECUTIVE;
    }
  }
}
