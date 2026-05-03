package cbs.dsl.script

import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.test.Test
import kotlin.test.assertTrue

class ScriptHostTest {
  private val host = ScriptHost()

  @Test
  fun `shouldCompileTrivialScriptWhenValidKotlin`() {
    val result = host.eval("val x = 1 + 1", "trivial.event.kts")
    assertTrue(
        result is ResultWithDiagnostics.Success,
        "Expected Success but got Failure: ${(result as? ResultWithDiagnostics.Failure)?.reports?.joinToString { it.message }}",
    )
  }

  @Test
  fun `shouldFailWhenSyntaxError`() {
    val result = host.eval("this is not valid kotlin !!!", "bad.event.kts")
    assertTrue(result is ResultWithDiagnostics.Failure)
  }
}
