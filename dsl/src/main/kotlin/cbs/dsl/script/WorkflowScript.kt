package cbs.dsl.script

import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "workflow.kts",
    compilationConfiguration = WorkflowScriptCompilationConfiguration::class,
)
abstract class WorkflowScript : WorkflowDslScope()
