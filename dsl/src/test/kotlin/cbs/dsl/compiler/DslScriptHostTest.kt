package cbs.dsl.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DslScriptHostTest {
  private val host = DslScriptHost()

  @Test
  fun `shouldEvalSimpleWorkflowScript`() {
    val script = """
      registry.register(
        workflow("test") {
          states("A", "B")
          initial("A")
          terminalStates("B")
        }
      )
    """.trimIndent()

    val registry = host.eval(script, "test.kts")

    assertEquals(1, registry.workflows.size)
    assertTrue(registry.workflows.containsKey("test"))
    val wf = registry.workflows["test"]!!
    assertEquals("test", wf.code)
    assertEquals(listOf("A", "B"), wf.states)
    assertEquals("A", wf.initial)
    assertEquals(listOf("B"), wf.terminalStates)
  }

  @Test
  fun `shouldThrowOnSyntaxError`() {
    val script = "this is not valid kotlin !!!"

    val exception = assertFailsWith<IllegalStateException> {
      host.eval(script, "bad.kts")
    }

    assertTrue(exception.message!!.contains("Script evaluation failed for 'bad.kts'"))
  }
}
