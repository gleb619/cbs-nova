package cbs.dsl.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a DSL implementation registered under a specific {@code code}.
 *
 * <p>The annotation processor reads this annotation at compile time and generates a
 * {@code *Definition} wrapper plus SPI registration code. Valid only on classes implementing
 * {@link TransactionFunction}, {@link HelperFunction}, or {@link ConditionFunction}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DslComponent {

  /**
   * Canonical code used to register and look up this component.
   *
   * @return the component code
   */
  String code() default "";

  /**
   * The type of DSL component — determines which registry map and generated artifacts apply.
   *
   * @return the component type
   */
  DslImplType type();
}
