package cbs.dsl.api

import io.avaje.jsonb.Json

/**
 * Consolidated condition DSL types.
 *
 * ConditionInput carries the raw parameters and event metadata for evaluating a condition.
 * ConditionOutput wraps the boolean result of the evaluation.
 */
@Json
@Suppress("UNCHECKED_CAST")
data class ConditionInput(
    val params: Map<String, Any?>,
    val eventCode: String? = null,
    val eventNumber: Long? = null,
) {

  /** Returns parameters with nulls filtered out, suitable for [TransactionContext]. */
  fun nonNullParams(): Map<String, Any> = params.filterValues { it != null } as Map<String, Any>
}

@Json data class ConditionOutput(val result: Boolean)
