package cbs.dsl.impl

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.MassOperationDefinition
import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.WorkflowDefinition
import cbs.dsl.api.WritableRegistry
import cbs.dsl.runtime.DslRegistry

/**
 * Runtime registry that maps string keys (code or name) to DSL definition instances.
 *
 * Lookup priority:
 * 1. Exact match on `name` (if the definition has a non-null name)
 * 2. Exact match on `code`
 *
 * This allows `.kts` files to call `ctx.helper("TestFindCustomerCode", ...)` and have it dispatch
 * to the `TestHelper` registered under that name, while also supporting the canonical
 * `ctx.helper("FIND_CUSTOMER_CODE_BY_ID", ...)` lookup by code.
 *
 * Registration is additive — registering a definition with the same code/name as an existing entry
 * overwrites it (last-write-wins). This supports test overrides of production beans.
 *
 * Implements [WritableRegistry] to support compile-time generated SPI registration from
 * [cbs.dsl.api.ImplRegistrationProvider] implementations.
 */
class ImplRegistry : WritableRegistry {
  private val byCode = mutableMapOf<String, TransactionDefinition>()
  private val byName = mutableMapOf<String, TransactionDefinition>()
  private val helperByCode = mutableMapOf<String, HelperDefinition>()
  private val helperByName = mutableMapOf<String, HelperDefinition>()
  private val conditionByCode = mutableMapOf<String, ConditionDefinition>()
  private val workflowByCode = mutableMapOf<String, WorkflowDefinition>()
  private val massOpByCode = mutableMapOf<String, MassOperationDefinition>()

  /**
   * Class-name keyed map for fast lookup without reflection. Used by [ImportResolver] in STRICT
   * mode to resolve CODE imports.
   */
  private val byClassName = mutableMapOf<String, Any>()

  override fun register(t: TransactionDefinition) {
    byCode[t.code] = t
    t.name?.let { byName[it] = t }
    byClassName[t::class.java.name] = t
  }

  override fun register(h: HelperDefinition) {
    helperByCode[h.code] = h
    h.name?.let { helperByName[it] = h }
    byClassName[h::class.java.name] = h
  }

  override fun register(c: ConditionDefinition) {
    conditionByCode[c.code] = c
    byClassName[c::class.java.name] = c
  }

  override fun register(w: WorkflowDefinition) {
    workflowByCode[w.code] = w
    byClassName[w::class.java.name] = w
  }

  override fun register(m: MassOperationDefinition) {
    massOpByCode[m.code] = m
    byClassName[m::class.java.name] = m
  }

  /**
   * Resolve a DSL definition by its fully-qualified class name. Used by [ImportResolver] in STRICT
   * mode to resolve CODE imports without reflection.
   *
   * @param fqcn fully-qualified class name (e.g., "cbs.dsl.impl.LoanConditionsByIdHelper")
   * @return the registered instance, or null if not found
   */
  fun resolveByClassName(fqcn: String): Any? = byClassName[fqcn]

  /**
   * Resolve all DSL definitions whose class names start with the given package prefix. Used by
   * [ImportResolver] in STRICT mode to resolve wildcard CODE imports.
   *
   * @param prefix package prefix (e.g., "cbs.dsl.impl")
   * @return list of matching registered instances
   */
  fun resolveByPackagePrefix(prefix: String): List<Any> =
      byClassName.filterKeys { it.startsWith(prefix + ".") }.values.toList()

  /** Resolve a transaction by name first, then by code. Throws if not found. */
  fun resolveTransaction(key: String): TransactionDefinition =
      byName[key]
          ?: byCode[key]
          ?: throw IllegalArgumentException("Transaction '$key' not found in ImplRegistry")

  /** Resolve a helper by name first, then by code. Throws if not found. */
  fun resolveHelper(key: String): HelperDefinition =
      helperByName[key]
          ?: helperByCode[key]
          ?: throw IllegalArgumentException("Helper '$key' not found in ImplRegistry")

  /** Resolve a condition by code. Throws if not found. */
  fun resolveCondition(key: String): ConditionDefinition =
      conditionByCode[key]
          ?: throw IllegalArgumentException("Condition '$key' not found in ImplRegistry")

  /** Resolve a workflow by code. Throws if not found. */
  fun resolveWorkflow(key: String): WorkflowDefinition =
      workflowByCode[key]
          ?: throw IllegalArgumentException("Workflow '$key' not found in ImplRegistry")

  /** Resolve a mass operation by code. Throws if not found. */
  fun resolveMassOperation(key: String): MassOperationDefinition =
      massOpByCode[key]
          ?: throw IllegalArgumentException("MassOperation '$key' not found in ImplRegistry")
}

/**
 * Populate an [ImplRegistry] from a [DslRegistry].
 *
 * Registers all transactions, helpers, and conditions from the [dslRegistry] into this registry,
 * keyed by both their [code] and [name] (if set).
 */
fun ImplRegistry.populateFrom(dslRegistry: DslRegistry) {
  dslRegistry.transactions.values.forEach { register(it) }
  dslRegistry.helpers.values.forEach { register(it) }
  dslRegistry.conditions.values.forEach { register(it) }
}
