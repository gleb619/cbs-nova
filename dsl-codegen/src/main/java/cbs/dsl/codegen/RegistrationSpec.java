package cbs.dsl.codegen;

public record RegistrationSpec(
    String packageName, String className, String code, DslInterfaceType interfaceType) {}
