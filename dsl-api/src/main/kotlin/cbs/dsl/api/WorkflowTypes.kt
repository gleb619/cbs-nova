package cbs.dsl.api

import io.avaje.jsonb.Json

/**
 * Input carrier for workflow state transition execution. Carries the current state, action to
 * perform, parameters, and optional workflow instance metadata as JSON-serializable data for
 * Temporal workflow replay and PostgreSQL JSONB storage.
 */
@Json
data class WorkflowInput(
    val currentState: String,
    val action: String,
    val params: Map<String, Any?> = emptyMap(),
    val workflowInstanceId: String? = null,
)

/**
 * Output carrier for workflow state transition execution. Carries the next state, event codes to
 * execute, and status as JSON-serializable data for Temporal workflow replay and PostgreSQL JSONB
 * storage.
 */
@Json
data class WorkflowOutput(
    val nextState: String,
    val events: List<String> = emptyList(),
    val status: String = "SUCCESS",
)
