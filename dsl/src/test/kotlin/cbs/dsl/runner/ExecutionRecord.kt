package cbs.dsl.runner

data class ExecutionRecord(
    val enrichment: Map<String, Any>,
    val txResults: List<String>,
    val error: Throwable?,
)
