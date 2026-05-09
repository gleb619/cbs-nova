package cbs.dsl.codegen;

import cbs.dsl.api.DslComponent.DslComponentModel;

public record RegistrationModel(
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

  public RegistrationModel(
      String packageName,
      String className,
      String code,
      DslInterfaceType interfaceType,
      String inputType,
      String outputType,
      DslComponentModel componentModel,
      String dslBody,
      String dslImports) {
    this(
        packageName,
        className,
        code,
        interfaceType,
        inputType,
        outputType,
        componentModel,
        dslBody,
        dslImports,
        null);
  }

  public RegistrationModel(
      String packageName,
      String className,
      String code,
      DslInterfaceType interfaceType,
      String inputType,
      String outputType,
      DslComponentModel componentModel) {
    this(
        packageName,
        className,
        code,
        interfaceType,
        inputType,
        outputType,
        componentModel,
        null,
        null,
        null);
  }
}
