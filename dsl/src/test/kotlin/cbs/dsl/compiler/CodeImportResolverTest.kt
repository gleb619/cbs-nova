package cbs.dsl.compiler

import cbs.dsl.api.DslComponent
import cbs.dsl.api.DslImplType
import cbs.dsl.api.DslTypes.ImportType
import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.HelperTypes.HelperInput
import cbs.dsl.api.HelperTypes.HelperOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName

@DslComponent(code = "TestHelper", type = DslImplType.HELPER)
class TestCodeHelper : HelperDefinition {
  override fun getCode(): String = "TestHelper"

  override fun execute(input: HelperInput): HelperOutput = HelperOutput("test")
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
    assertEquals("TestHelper", (result[0] as TestCodeHelper).code)
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
