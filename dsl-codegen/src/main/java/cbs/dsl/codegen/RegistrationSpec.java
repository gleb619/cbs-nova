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
    String dslImports,
    String sourceCode) {

  public RegistrationSpec(
      String packageName,
      String className,
      String code,
      DslInterfaceType interfaceType,
      String inputType,
      String outputType,
      DslComponentModel componentModel,
      String dslBody,
      String dslImports) {
    this(packageName, className, code, interfaceType, inputType, outputType, componentModel, dslBody, dslImports, null);
  }

  public RegistrationSpec(
      String packageName,
      String className,
      String code,
      DslInterfaceType interfaceType,
      String inputType,
      String outputType,
      DslComponentModel componentModel) {
    this(packageName, className, code, interfaceType, inputType, outputType, componentModel, null, null, null);
  }
}
