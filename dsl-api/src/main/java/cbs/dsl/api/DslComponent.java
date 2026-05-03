package cbs.dsl.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a DSL implementation registered under a specific {@code code}.
 *
 * <p>The annotation processor (or Spring auto-configuration) scans for classes annotated with
 * {@code @DslComponent} and registers them into the {@code ImplRegistry} at startup. The
 * {@code code} must match the string used in DSL files: {@code transaction("KYC_CHECK")},
 * {@code helper("LOAN_CONDITIONS_BY_ID")}, etc.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @DslComponent(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
 * class KycCheckTransaction implements TransactionDefinition {
 *     @Override
 *     public String getCode() { return "KYC_CHECK"; }
 *     @Override
 *     public void execute(TransactionContext ctx) { ... }
 * }
 * }</pre>
 *
 * In tests, {@code TestTransaction} / {@code TestHelper} / {@code TestCondition} can be annotated
 * with {@code @DslComponent} to participate in compile-time registration.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DslComponent {
  String code();

  DslImplType type();
}
