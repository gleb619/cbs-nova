package cbs.dsl.codegen;

import cbs.dsl.api.DslComponent.DslComponentModel;

public record RegistrationSpec(
    String packageName,
    String className,
    String code,
    DslInterfaceType interfaceType,
    String inputType,
    String outputType,
    DslComponentModel componentModel) {}
