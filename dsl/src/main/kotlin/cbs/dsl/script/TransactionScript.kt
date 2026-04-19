package cbs.dsl.script

import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "transaction.kts",
    compilationConfiguration = TransactionScriptCompilationConfiguration::class,
)
abstract class TransactionScript
