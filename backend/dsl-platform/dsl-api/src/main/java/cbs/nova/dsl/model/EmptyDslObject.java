package cbs.nova.dsl.model;

import cbs.nova.dsl.DslObject;

public final class EmptyDslObject implements DslObject {

  public String name() {
    return "empty";
  }

  public DslType type() {
    return DslType.OTHER;
  }

  public static EmptyDslObject emptyDslObject() {
    return new EmptyDslObject();
  }

}
