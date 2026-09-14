package cbs.nova.dsl.model;

import cbs.nova.dsl.DslDescriptor;

public interface ObjectDescriptor {

  String name();

  Class<?> inputType();

  Class<?> outputType();

  default DslDescriptor toDslDescriptor() {
    return DslDescriptor.builder()
            .name(name())
            .inputType(inputType())
            .outputType(outputType())
            .objectDescriptor(this)
            .build();
  }

}
