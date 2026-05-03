package cbs.dsl.compiler

data class ImportDirective(
    val path: String,
    val alias: String?,
    val wildcard: Boolean,
)
