package cbs.dsl.script

import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "event.kts",
    compilationConfiguration = EventScriptCompilationConfiguration::class,
)
abstract class EventScript : EventDslScope()
