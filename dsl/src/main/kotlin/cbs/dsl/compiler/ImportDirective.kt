package cbs.dsl.compiler

import cbs.dsl.api.ImportType

data class ImportDirective(
    val path: String,
    val alias: String?,
    val wildcard: Boolean,
    val type: ImportType = ImportType.DSL,
)
