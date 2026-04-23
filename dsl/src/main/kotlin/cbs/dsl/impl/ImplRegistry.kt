package cbs.dsl.impl

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.TransactionDefinition
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
 */
class ImplRegistry {
  private val byCode = mutableMapOf<String, TransactionDefinition>()
  private val byName = mutableMapOf<String, TransactionDefinition>()
  private val helperByCode = mutableMapOf<String, HelperDefinition>()
  private val helperByName = mutableMapOf<String, HelperDefinition>()
  private val conditionByCode = mutableMapOf<String, ConditionDefinition>()

  fun register(t: TransactionDefinition) {
    byCode[t.code] = t
    t.name?.let { byName[it] = t }
  }

  fun register(h: HelperDefinition) {
    helperByCode[h.code] = h
    h.name?.let { helperByName[it] = h }
  }

  fun register(c: ConditionDefinition) {
    conditionByCode[c.code] = c
  }

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
