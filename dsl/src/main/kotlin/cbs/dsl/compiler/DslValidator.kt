package cbs.dsl.compiler

import cbs.dsl.api.EventDefinition
import cbs.dsl.api.TransitionRule
import cbs.dsl.api.WorkflowDefinition
import cbs.dsl.runtime.DslRegistry
import cbs.dsl.runtime.TransactionBuilder

// TODO: Call method on gradle compilation stage
class DslValidator {
    fun validate(
        registry: DslRegistry,
        fileName: String,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        for ((code, wf) in registry.workflows) {
            validateWorkflow(wf, code, registry.events, fileName, errors)
        }

        for ((code, massOp) in registry.massOperations) {
            if (massOp.source == null) {
                errors += ValidationError(fileName, "MassOperation '$code': source is not defined")
            }
        }

        if (fileName.endsWith(".transaction.kts")) {
            if (registry.transactions.isEmpty()) {
                errors += ValidationError(fileName, "Transaction script must define exactly one 'transaction { }' block")
            } else if (registry.transactions.size > 1) {
                errors += ValidationError(fileName, "Transaction script defines multiple 'transaction { }' blocks")
            }

            for ((code, tx) in registry.transactions) {
                if (tx is TransactionBuilder && !tx.hasExecute) {
                    errors += ValidationError(fileName, "Transaction '$code' has no execute block defined")
                }
            }
        }

        return errors
    }

    private fun validateWorkflow(
        wf: WorkflowDefinition,
        code: String,
        registeredEvents: Map<String, EventDefinition>,
        fileName: String,
        errors: MutableList<ValidationError>,
    ) {
        val stateSet = wf.states.toSet()

        // Check initial state
        if (wf.initial.isBlank() || wf.initial !in stateSet) {
            errors += ValidationError(fileName, "Workflow '$code': initial state '${wf.initial}' not in states")
        }

        // Check terminal states
        for (terminal in wf.terminalStates) {
            if (terminal !in stateSet) {
                errors += ValidationError(fileName, "Workflow '$code': terminal state '$terminal' not in states")
            }
        }

        // Check transitions
        for (transition in wf.transitions) {
            validateTransition(transition, code, stateSet, registeredEvents, fileName, errors)
        }
    }

    private fun validateTransition(
        transition: TransitionRule,
        workflowCode: String,
        stateSet: Set<String>,
        registeredEvents: Map<String, EventDefinition>,
        fileName: String,
        errors: MutableList<ValidationError>,
    ) {
        if (transition.from !in stateSet) {
            errors +=
                ValidationError(
                    fileName,
                    "Workflow '$workflowCode': transition from '${transition.from}' references unknown state",
                )
        }

        if (transition.to !in stateSet) {
            errors +=
                ValidationError(
                    fileName,
                    "Workflow '$workflowCode': transition to '${transition.to}' references unknown state",
                )
        }

        if (transition.event.code !in registeredEvents) {
            errors +=
                ValidationError(
                    fileName,
                    "Workflow '$workflowCode': transition event '${transition.event.code}' not registered",
                )
        }
    }
}
