package cbs.dsl.api

import io.avaje.jsonb.Json

/**
 * Input carrier for event execution. Carries event parameters and execution metadata as
 * JSON-serializable data for Temporal workflow replay and PostgreSQL JSONB storage.
 */
@Json
data class EventInput(
    val params: Map<String, Any?>,
    val eventCode: String,
    val eventNumber: Long? = null,
    val workflowExecutionId: String? = null,
)

/**
 * Output carrier for event execution. Carries the enriched context and transaction results as
 * JSON-serializable data for Temporal workflow replay and PostgreSQL JSONB storage.
 */
@Json
data class EventOutput(
    val context: Map<String, Any?>,
    val transactionResults: Map<String, Map<String, Any?>>,
    val status: String = "SUCCESS",
)
