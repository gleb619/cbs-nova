package cbs.dsl.compiler

import cbs.dsl.api.DslComponent
import cbs.dsl.api.DslImplType
import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.HelperInput
import cbs.dsl.api.HelperOutput
import cbs.dsl.api.ImportType
import cbs.dsl.runtime.AnyHelperOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName

@DslComponent(code = "TEST_HELPER", type = DslImplType.HELPER)
class TestCodeHelper : HelperDefinition {
  override val code = "TEST_HELPER"

  override fun execute(input: HelperInput): HelperOutput = AnyHelperOutput("test")
}

class CodeImportResolverTest {
  private val resolver = CodeImportResolver()

  @Test
  @DisplayName("shouldResolveAnnotatedClass")
  fun `shouldResolveAnnotatedClass`() {
    val directive = ImportDirective("cbs.dsl.compiler.TestCodeHelper", null, false, ImportType.CODE)
    val result = resolver.resolve(directive)

    assertEquals(1, result.size)
    assertTrue(result[0] is TestCodeHelper)
    assertEquals("TEST_HELPER", (result[0] as TestCodeHelper).code)
  }

  @Test
  @DisplayName("shouldNotResolveNonAnnotatedClass")
  fun `shouldNotResolveNonAnnotatedClass`() {
    val directive = ImportDirective("java.lang.String", null, false, ImportType.CODE)
    val result = resolver.resolve(directive)

    assertTrue(result.isEmpty())
  }

  @Test
  @DisplayName("shouldResolvePackageWildcard")
  fun `shouldResolvePackageWildcard`() {
    val directive = ImportDirective("cbs.dsl.compiler", null, true, ImportType.CODE)
    val result = resolver.resolve(directive)

    // Should find at least TestCodeHelper
    assertTrue(result.any { it is TestCodeHelper })
  }
}
