package cbs.nova.starter.model;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.model.CompileDiagnostic;

public record RuntimeOutcome(boolean success, Object value, ErrorResponse error, boolean replayed) {

  public static RuntimeOutcome ok(Object value) {
    return new RuntimeOutcome(true, value, null, false);
  }

  public static RuntimeOutcome error(ErrorResponse error) {
    return new RuntimeOutcome(false, null, error, false);
  }

  public static RuntimeOutcome okReplayed(Object value) {
    return new RuntimeOutcome(true, value, null, true);
  }
}
