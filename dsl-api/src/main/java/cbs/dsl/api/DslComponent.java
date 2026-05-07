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
 *
 * <p>The {@link #componentModel()} attribute controls how the generated wrapper obtains the
 * component instance at runtime:
 *
 * <ul>
 *   <li>{@link DslComponentModel#SIMPLE} — plain constructor {@code new}.
 *   <li>{@link DslComponentModel#SPRING} — looked up from Spring {@code ApplicationContext}.
 *   <li>{@link DslComponentModel#AUTO} — inspect class for Spring annotations at compile time
 *       (default).
 * </ul>
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

  /**
   * Component instantiation model — SIMPLE (plain constructor), SPRING (bean lookup), or AUTO
   * (detect Spring annotations at compile time, default).
   *
   * @return the component model
   */
  DslComponentModel componentModel() default DslComponentModel.AUTO;

  /**
   * Defines the type of DSL implementation being registered.
   *
   * <p>This tells the annotation processor (or Spring scanner) which registry map to populate in the
   * {@code ImplRegistry}.
   */
  enum DslImplType {

    //TODO: add AUTO, detect type based on interface

    /**
     * A transaction implementation that will be registered in the transaction registry. Used for
     * classes implementing {@link TransactionFunction}.
     */
    TRANSACTION,

    /**
     * A helper implementation that will be registered in the helper registry. Used for classes
     * implementing {@link HelperFunction}.
     */
    HELPER,

    /**
     * A condition implementation that will be registered in the condition registry. Used for classes
     * implementing {@link ConditionFunction}.
     */
    CONDITION,

    /**
     * An event implementation that will be registered in the event registry. Used for classes
     * implementing {@link EventFunction}.
     */
    EVENT,

    /**
     * A workflow implementation that will be registered in the workflow registry. Used for classes
     * implementing {@link WorkflowFunction}.
     */
    WORKFLOW,

    /**
     * A mass operation implementation that will be registered in the mass operation registry. Used
     * for classes implementing {@link MassOperationFunction}.
     */
    MASS_OPERATION,
  }

  /**
   * Determines how the generated {@code *Definition} wrapper obtains the underlying component
   * instance at runtime.
   *
   * <ul>
   *   <li>{@code SIMPLE} — plain constructor {@code new MyClass()} (no Spring container required).
   *   <li>{@code SPRING} — resolved from the Spring {@code ApplicationContext} via
   *       {@link DslComponentResolver}.
   *   <li>{@code AUTO} — inspect the annotated class at compile time; if it carries any
   *       {@code org.springframework.*} annotation → {@code SPRING}, otherwise {@code SIMPLE}.
   * </ul>
   */
  enum DslComponentModel {
    /** Inspect class for Spring annotations at compile time to choose SIMPLE or SPRING. */
    AUTO,

    /** Plain constructor instantiation — no Spring container required. */
    SIMPLE,

    /** Resolved from Spring {@code ApplicationContext} via {@link DslComponentResolver}. */
    SPRING,

  }
}
