package cbs.dsl.script

import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "mass.kts",
    compilationConfiguration = MassOperationScriptCompilationConfiguration::class,
)
abstract class MassOperationScript : MassOperationDslScope()
