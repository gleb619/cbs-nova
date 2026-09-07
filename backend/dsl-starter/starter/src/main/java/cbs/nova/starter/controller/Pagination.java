package cbs.nova.starter.controller;

import org.springframework.web.servlet.function.ServerRequest;

/**
 * Shared pagination parameter parsing and clamping for functional DSL handlers.
 */
public final class Pagination {

  public static final int DEFAULT_LIMIT = 50;
  public static final int DEFAULT_OFFSET = 0;
  public static final int MAX_LIMIT = 500;

  private Pagination() {
    // utility class
  }

  public static int clampLimit(int limit) {
    return Math.max(1, Math.min(limit, MAX_LIMIT));
  }

  public static int clampOffset(int offset) {
    return Math.max(0, offset);
  }

  public static int intParam(ServerRequest request, String name, int defaultValue) {
    var raw = request.param(name).filter(s -> !s.isBlank()).orElse(null);
    if (raw == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
              "Invalid value for query parameter '" + name + "': '" + raw
                      + "' (expected an integer)");
    }
  }
}
