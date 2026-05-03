package cbs.dsl.compiler

import org.junit.jupiter.api.DisplayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportParserTest {
    @Test
    @DisplayName("shouldParseWildcardImportWithAlias")
    fun `shouldParseWildcardImportWithAlias`() {
        val result = ImportParser.parse("// #import loan-disbursement.* as disb")
        assertEquals(1, result.size)
        assertEquals(ImportDirective("loan-disbursement", "disb", true), result[0])
    }

    @Test
    @DisplayName("shouldParseNamedImportWithoutAlias")
    fun `shouldParseNamedImportWithoutAlias`() {
        val result = ImportParser.parse("// #import global.banking-helpers")
        assertEquals(1, result.size)
        assertEquals(ImportDirective("global.banking-helpers", null, false), result[0])
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
            """.trimIndent()
        val result = ImportParser.parse(content)
        assertEquals(3, result.size)
        assertEquals(ImportDirective("loan-disbursement", "disb", true), result[0])
        assertEquals(ImportDirective("global.banking-helpers", null, false), result[1])
        assertEquals(ImportDirective("payments", "pay", true), result[2])
    }
}
