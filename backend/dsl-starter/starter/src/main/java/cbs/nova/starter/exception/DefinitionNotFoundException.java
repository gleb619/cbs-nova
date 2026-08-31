package cbs.nova.starter.exception;

import lombok.Getter;

/**
 * Thrown when a DSL operation references a definition that has not been published to the
 * generated-class registry.
 */
@Getter
public class DefinitionNotFoundException extends RuntimeException {

  private final String entityName;

  public DefinitionNotFoundException(String entityName) {
    super("No published definition: " + entityName);
    this.entityName = entityName;
  }
}
