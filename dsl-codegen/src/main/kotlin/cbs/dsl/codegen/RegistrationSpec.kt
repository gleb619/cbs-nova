package cbs.dsl.codegen

/**
 * Represents a DSL component to be registered in the [cbs.dsl.impl.ImplRegistry].
 *
 * @param packageName The package containing the annotated class.
 * @param className The simple name of the annotated class.
 * @param code The code identifier from the [cbs.dsl.api.DslComponent] annotation.
 * @param interfaceType The type of DSL definition interface implemented.
 */
data class RegistrationSpec(
    val packageName: String,
    val className: String,
    val code: String,
    val interfaceType: DslInterfaceType,
)

/** Enumerates the supported DSL definition interfaces that can be registered. */
enum class DslInterfaceType {
  TRANSACTION,
  HELPER,
  CONDITION,
  WORKFLOW,
  MASS_OPERATION,
}
