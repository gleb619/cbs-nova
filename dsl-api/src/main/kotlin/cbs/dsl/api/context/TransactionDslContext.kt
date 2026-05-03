package cbs.dsl.api.context

import cbs.dsl.api.TransactionDefinition

enum class TransactionPhase {
    PREVIEW,
    EXECUTE,
    ROLLBACK,
}

class TransactionDslContext(
    eventCode: String,
    workflowExecutionId: Long,
    performedBy: String,
    dslVersion: String,
    eventParameters: Map<String, Any>,
    isResumed: Boolean,
    private val delegateTarget: TransactionDefinition?,
    private val currentPhase: TransactionPhase,
) : TransactionContext(eventCode, workflowExecutionId, performedBy, dslVersion, eventParameters, isResumed) {
    constructor(
        source: TransactionContext,
        delegateTarget: TransactionDefinition?,
        phase: TransactionPhase,
    ) : this(
        source.eventCode,
        source.workflowExecutionId,
        source.performedBy,
        source.dslVersion,
        source.eventParameters,
        source.isResumed,
        delegateTarget,
        phase,
    ) {
        enrichment.putAll(source.enrichment)
    }

    override fun delegate() {
        when (currentPhase) {
            TransactionPhase.PREVIEW -> delegateTarget?.preview(this)
            TransactionPhase.EXECUTE -> delegateTarget?.execute(this)
            TransactionPhase.ROLLBACK -> delegateTarget?.rollback(this)
        }
    }
}
