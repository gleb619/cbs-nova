package cbs.dsl.script

import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

object HelperScriptCompilationConfiguration :
    ScriptCompilationConfiguration({
      baseClass(HelperDslScope::class)
      jvm { dependenciesFromCurrentContext(wholeClasspath = true) }
      defaultImports(
          "cbs.dsl.api.*",
          "cbs.dsl.api.context.*",
          "cbs.dsl.script.*",
          "cbs.dsl.runtime.*",
      )
    })
