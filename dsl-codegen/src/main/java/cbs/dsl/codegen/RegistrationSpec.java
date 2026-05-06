package cbs.dsl.codegen;

import cbs.dsl.api.DslComponent.DslComponentModel;

public record RegistrationSpec(
    String packageName,
    String className,
    String code,
    DslInterfaceType interfaceType,
    String inputType,
    String outputType,
    DslComponentModel componentModel,
    String dslBody,
    String dslImports) {

  public RegistrationSpec(
      String packageName,
      String className,
      String code,
      DslInterfaceType interfaceType,
      String inputType,
      String outputType,
      DslComponentModel componentModel) {
    this(packageName, className, code, interfaceType, inputType, outputType, componentModel, null, null);
  }
}
