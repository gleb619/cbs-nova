package cbs.dsl.api

/**
 * Marks a class as a DSL implementation registered under a specific [code].
 *
 * The annotation processor (or Spring auto-configuration) scans for classes annotated with
 * `@DslImpl` and registers them into the `ImplRegistry` at startup. The [code] must match
 * the string used in DSL files: `transaction("KYC_CHECK")`, `helper("LOAN_CONDITIONS_BY_ID")`, etc.
 *
 * Example:
 * ```kotlin
 * @DslImpl(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
 * class KycCheckTransaction : TransactionDefinition {
 *     override val code = "KYC_CHECK"
 *     override fun execute(ctx: TransactionContext) { ... }
 * }
 * ```
 *
 * In tests, `TestTransaction` / `TestHelper` / `TestCondition` can be annotated with `@DslImpl`
 * to participate in compile-time registration:
 * ```kotlin
 * @DslImpl(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
 * class TestKycCheck : TransactionDefinition { ... }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DslImpl(
    val code: String,
    val type: DslImplType,
)

/**
 * Defines the type of DSL implementation being registered.
 *
 * This tells the annotation processor (or Spring scanner) which registry map to populate
 * in the `ImplRegistry`.
 */
enum class DslImplType {
    /**
     * A transaction implementation that will be registered in the transaction registry.
     * Used for classes implementing [TransactionDefinition].
     */
    TRANSACTION,

    /**
     * A helper implementation that will be registered in the helper registry.
     * Used for classes implementing [HelperDefinition].
     */
    HELPER,

    /**
     * A condition implementation that will be registered in the condition registry.
     * Used for classes implementing [ConditionDefinition].
     */
    CONDITION,

    /**
     * An event implementation that will be registered in the event registry.
     * Used for classes implementing [EventDefinition].
     */
    EVENT,
}
