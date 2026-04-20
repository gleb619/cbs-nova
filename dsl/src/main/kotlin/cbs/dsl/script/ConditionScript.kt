package cbs.dsl.script

import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "condition.kts",
    compilationConfiguration = ConditionScriptCompilationConfiguration::class,
)
abstract class ConditionScript : ConditionDslScope()
