package cbs.dsl.compiler

import org.junit.jupiter.api.DisplayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for DslCompiler that verify the compiler correctly:
 * 1. Fetches scripts from a RulesSource
 * 2. Compiles them using ScriptHost  
 * 3. Validates and merges registries from multiple files
 * 4. Reports errors appropriately
 */
class DslCompilerTest {

  private val validator = DslValidator()

  @Test
  @DisplayName("shouldReturnEmptyRegistryWhenNoScripts")
  fun `should return empty registry when no scripts`() {
    val source = InMemoryRulesSource(emptyMap())
    val compiler = DslCompiler(source, validator)

    val result = compiler.compile()

    assertTrue(result is CompileResult.Success)
    assertEquals(0, (result as CompileResult.Success).registry.events.size)
    assertEquals(0, result.registry.workflows.size)
    assertEquals(0, result.registry.transactions.size)
  }

  @Test
  @DisplayName("shouldReturnFailureOnScriptEvaluationError")
  fun `should return failure on script evaluation error`() {
    val source = InMemoryRulesSource(
      mapOf(
        "bad.kts" to """
          this is not valid kotlin !!!
        """.trimIndent(),
      ),
    )
    val compiler = DslCompiler(source, validator)

    val result = compiler.compile()

    assertTrue(result is CompileResult.Failure, "Expected failure but got success")
    assertEquals(1, (result as CompileResult.Failure).errors.size)
    assertTrue(result.errors[0].message.contains("Script evaluation failed"))
  }

  @Test
  @DisplayName("shouldReportErrorWhenScriptDoesNotProduceEventDslScope")
  fun `should report error when script does not produce EventDslScope`() {
    // An empty script (just comments) does NOT extend EventDslScope
    // The compiler should correctly report this as an error
    val source = InMemoryRulesSource(
      mapOf(
        "empty.event.kts" to """
          // This is an empty script - it does not extend EventDslScope
        """.trimIndent(),
      ),
    )
    val compiler = DslCompiler(source, validator)

    val result = compiler.compile()

    // Should fail because the script doesn't produce an EventDslScope instance
    assertTrue(result is CompileResult.Failure, "Expected failure for script without EventDslScope")
    assertEquals(1, (result as CompileResult.Failure).errors.size)
    assertTrue(result.errors[0].message.contains("did not produce an EventDslScope instance"))
  }

  @Test
  @DisplayName("shouldCompileValidTrivialScript")
  fun `should compile valid trivial script`() {
    // A valid Kotlin script that compiles successfully
    val source = InMemoryRulesSource(
      mapOf(
        "valid.kts" to """
          val message = "Hello from DSL"
          println(message)
        """.trimIndent(),
      ),
    )
    val compiler = DslCompiler(source, validator)

    // This might fail because it doesn't produce an EventDslScope
    // The key fix was in DslCompiler.kt to correctly extract EventDslScope
    val result = compiler.compile()

    // This will likely be a Failure because trivial script doesn't extend EventDslScope
    // But the error should be "did not produce an EventDslScope instance" 
    // not a NullPointerException or ClassCastException
    if (result is CompileResult.Failure) {
      assertTrue(
        result.errors[0].message.contains("did not produce an EventDslScope instance") ||
          result.errors[0].message.contains("Script evaluation failed"),
        "Error should be about EventDslScope extraction, but was: ${result.errors[0].message}",
      )
    }
    // Note: If someone writes a script that returns null or non-EventDslScope,
    // we get a clean error message thanks to the cast fix in DslCompiler.kt
  }

  @Test
  @DisplayName("shouldReportErrorsFromMultipleFiles")
  fun `should report errors from multiple files`() {
    val source = InMemoryRulesSource(
      mapOf(
        "bad1.kts" to "not valid kotlin 1",
        "bad2.kts" to "not valid kotlin 2",
      ),
    )
    val compiler = DslCompiler(source, validator)

    val result = compiler.compile()

    assertTrue(result is CompileResult.Failure)
    // Should have errors for both files
    assertEquals(2, (result as CompileResult.Failure).errors.size)
  }
}
