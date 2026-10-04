package cbs.nova.dsl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Helper {

  String name();

  /**
   * One-line, human-readable summary of what the helper does. Rendered in the generated helpers
   * catalog and in {@code /api/dsl/objects/search}. Defaults to {@code ""} so existing helpers keep
   * compiling; when blank the catalog falls back to {@link cbs.nova.dsl.Executable#description()}.
   *
   * @return one-line description, never {@code null}
   */
  String description() default "";

  ComponentModel componentModel() default ComponentModel.STANDARD;

  CreationStrategy creationStrategy() default CreationStrategy.STANDARD;

  enum ComponentModel {

    STANDARD, LAZY

  }

  enum CreationStrategy {

    STANDARD, FACTORY

  }

}
