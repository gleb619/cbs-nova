package cbs.dsl.script

import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "helper.kts",
    compilationConfiguration = HelperScriptCompilationConfiguration::class,
)
abstract class HelperScript : HelperDslScope()
