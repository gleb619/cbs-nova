package cbs.dsl.compiler

import cbs.dsl.api.DslTypes.ImportType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName

class ImportParserTest {
  @Test
  @DisplayName("shouldParseWildcardImportWithAlias")
  fun `shouldParseWildcardImportWithAlias`() {
    val result = ImportParser.parse("// #import loan-disbursement.* as disb")
    assertEquals(1, result.size)
    assertEquals(ImportDirective("loan-disbursement", "disb", true, ImportType.DSL), result[0])
  }

  @Test
  @DisplayName("shouldParseCodeImport")
  fun `shouldParseCodeImport`() {
    val result = ImportParser.parse("// #import code:com.example.MyHelper")
    assertEquals(1, result.size)
    assertEquals(ImportDirective("com.example.MyHelper", null, false, ImportType.CODE), result[0])
  }

  @Test
  @DisplayName("shouldParseCodeWildcardImportWithAlias")
  fun `shouldParseCodeWildcardImportWithAlias`() {
    val result = ImportParser.parse("// #import code:com.example.* as ex")
    assertEquals(1, result.size)
    assertEquals(ImportDirective("com.example", "ex", true, ImportType.CODE), result[0])
  }

  @Test
  @DisplayName("shouldParseNamedImportWithoutAlias")
  fun `shouldParseNamedImportWithoutAlias`() {
    val result = ImportParser.parse("// #import global.banking-helpers")
    assertEquals(1, result.size)
    assertEquals(ImportDirective("global.banking-helpers", null, false, ImportType.DSL), result[0])
  }

  @Test
  @DisplayName("shouldSkipFrameworkImports")
  fun `shouldSkipFrameworkImports`() {
    val result = ImportParser.parse("// #import framework.ExecutionContext")
    assertTrue(result.isEmpty())
  }

  @Test
  @DisplayName("shouldIgnoreNonImportComments")
  fun `shouldIgnoreNonImportComments`() {
    val result = ImportParser.parse("// regular comment")
    assertTrue(result.isEmpty())
  }

  @Test
  @DisplayName("shouldParseMultipleImportsFromContent")
  fun `shouldParseMultipleImportsFromContent`() {
    val content =
        """
        // #import loan-disbursement.* as disb
        // some other comment
        // #import global.banking-helpers
        val x = 1
        // #import payments.* as pay
        """
            .trimIndent()
    val result = ImportParser.parse(content)
    assertEquals(3, result.size)
    assertEquals(ImportDirective("loan-disbursement", "disb", true, ImportType.DSL), result[0])
    assertEquals(ImportDirective("global.banking-helpers", null, false, ImportType.DSL), result[1])
    assertEquals(ImportDirective("payments", "pay", true, ImportType.DSL), result[2])
  }
}
