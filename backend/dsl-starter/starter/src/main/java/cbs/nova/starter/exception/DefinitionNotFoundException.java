package cbs.nova.starter.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Thrown when a DSL operation references a definition that has not been published to the
 * generated-class registry.
 */
@Getter
@RequiredArgsConstructor
public class DefinitionNotFoundException extends RuntimeException {

  private final String entityName;

  @Override
  public String getMessage() {
    return "No published definition: " + entityName;
  }
}
