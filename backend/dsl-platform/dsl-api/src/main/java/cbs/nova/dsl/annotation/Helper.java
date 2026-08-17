package cbs.nova.dsl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Helper {

  String name();

  ComponentModel componentModel() default ComponentModel.STANDARD;

  CreationStrategy creationStrategy() default CreationStrategy.STANDARD;

  enum ComponentModel {

    STANDARD,
    LAZY

  }

  enum CreationStrategy {

    STANDARD,
    FACTORY

  }

}
