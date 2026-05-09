package cbs.dsl.builder;

import cbs.dsl.api.DslObject;

public class UndefinedDslObject implements DslObject {

  @Override
  public String code() {
    return "undefined";
  }

  public static UndefinedDslObject create() {
    return new UndefinedDslObject();
  }

}
