package cbs.dsl.api

import io.avaje.jsonb.Json

/**
 * Consolidated transaction DSL types.
 *
 * TransactionInput carries the raw parameters and event metadata for executing a transaction.
 * TransactionOutput wraps the execution result and status.
 */
@Json
data class TransactionInput(
    val params: Map<String, Any?>,
    val eventCode: String? = null,
    val eventNumber: Long? = null,
    val workflowExecutionId: String? = null,
) {
  /** Returns parameters with nulls filtered out, suitable for [TransactionContext]. */
  fun nonNullParams(): Map<String, Any> = params.filterValues { it != null } as Map<String, Any>
}

@Json
data class TransactionOutput(
    val result: Map<String, Any?> = emptyMap(),
    val status: String = "SUCCESS",
)
