package cbs.dsl.script

import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

object EventScriptCompilationConfiguration :
    ScriptCompilationConfiguration({
      baseClass(EventDslScope::class)
      jvm { dependenciesFromCurrentContext(wholeClasspath = true) }
      defaultImports("cbs.dsl.api.*", "cbs.dsl.script.*", "cbs.dsl.runtime.*", "cbs.dsl.compiler.*")
    })
