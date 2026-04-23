package cbs.dsl.script

import cbs.dsl.api.WorkflowDefinition
import cbs.dsl.runtime.WorkflowBuilder

abstract class WorkflowDslScope {
  internal val registeredWorkflows: MutableList<WorkflowDefinition> = mutableListOf()

  fun workflow(code: String, block: WorkflowBuilder.() -> Unit): WorkflowDefinition {
    val def = WorkflowBuilder(code).apply(block)
    registeredWorkflows += def
    return def
  }
}
