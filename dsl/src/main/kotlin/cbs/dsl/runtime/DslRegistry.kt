package cbs.dsl.runtime

import cbs.dsl.api.ConditionDefinition
import cbs.dsl.api.EventDefinition
import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.MassOperationDefinition
import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.WorkflowDefinition

class DslRegistry {
  private val _workflows = mutableMapOf<String, WorkflowDefinition>()
  private val _events = mutableMapOf<String, EventDefinition>()
  private val _transactions = mutableMapOf<String, TransactionDefinition>()
  private val _massOperations = mutableMapOf<String, MassOperationDefinition>()
  private val _helpers = mutableMapOf<String, HelperDefinition>()
  private val _conditions = mutableMapOf<String, ConditionDefinition>()

  val workflows: Map<String, WorkflowDefinition>
    get() = _workflows.toMap()

  val events: Map<String, EventDefinition>
    get() = _events.toMap()

  val transactions: Map<String, TransactionDefinition>
    get() = _transactions.toMap()

  val massOperations: Map<String, MassOperationDefinition>
    get() = _massOperations.toMap()

  val helpers: Map<String, HelperDefinition>
    get() = _helpers.toMap()

  val conditions: Map<String, ConditionDefinition>
    get() = _conditions.toMap()

  fun register(w: WorkflowDefinition) {
    _workflows.registerChecked(w.code, w)
  }

  fun register(e: EventDefinition) {
    _events.registerChecked(e.code, e)
  }

  fun register(t: TransactionDefinition) {
    _transactions.registerChecked(t.code, t)
  }

  fun register(m: MassOperationDefinition) {
    _massOperations.registerChecked(m.code, m)
  }

  fun register(h: HelperDefinition) {
    _helpers.registerChecked(h.code, h)
  }

  fun register(c: ConditionDefinition) {
    _conditions.registerChecked(c.code, c)
  }

  private fun <T> MutableMap<String, T>.registerChecked(code: String, value: T) {
    require(!containsKey(code)) { "Duplicate registration for code '$code'" }
    put(code, value)
  }
}
