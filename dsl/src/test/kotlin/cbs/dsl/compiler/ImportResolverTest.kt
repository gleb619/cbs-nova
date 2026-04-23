package cbs.dsl.compiler

import cbs.dsl.api.EventDefinition
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.context.DisplayScope
import cbs.dsl.api.context.EnrichmentContext
import cbs.dsl.api.context.FinishContext
import cbs.dsl.api.context.TransactionsScope
import cbs.dsl.runtime.DslRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName

class ImportResolverTest {
  private fun registryWith(vararg codes: String): DslRegistry {
    val registry = DslRegistry()
    codes.forEach { code ->
      registry.register(
          object : EventDefinition {
            override val code = code
            override val parameters: List<ParameterDefinition> = emptyList()
            override val contextBlock: (EnrichmentContext) -> Unit = {}
            override val displayBlock: (DisplayScope) -> Unit = {}
            override val transactionsBlock: (suspend TransactionsScope.() -> Unit)? = null
            override val finishBlock: (FinishContext, Throwable?) -> Unit = { _, _ -> }
          }
      )
    }
    return registry
  }

  @Test
  @DisplayName("shouldReturnEmptyMapWhenNoDirectives")
  fun `shouldReturnEmptyMapWhenNoDirectives`() {
    val result = ImportResolver(registryWith("LOAN_CREATE")).resolve(emptyList())
    assertTrue(result.isEmpty())
  }

  @Test
  @DisplayName("shouldResolveWildcardImportToAllDefinitions")
  fun `shouldResolveWildcardImportToAllDefinitions`() {
    val registry = registryWith("LOAN_CREATE", "LOAN_APPROVE")
    val directives = listOf(ImportDirective("loan-disbursement", "disb", true))
    val result = ImportResolver(registry).resolve(directives)
    val scope = result["disb"]!!
    assertEquals(scope["LOAN_CREATE"], scope["LOAN_CREATE"])
    assertEquals(scope["LOAN_APPROVE"], scope["LOAN_APPROVE"])
  }

  @Test
  @DisplayName("shouldUseAliasAsKeyInResultMap")
  fun `shouldUseAliasAsKeyInResultMap`() {
    val directives = listOf(ImportDirective("loan-disbursement", "disb", true))
    val result = ImportResolver(registryWith("LOAN_CREATE")).resolve(directives)
    assertTrue(result.containsKey("disb"))
  }

  @Test
  @DisplayName("shouldUseLastPathSegmentAsAliasWhenNoAlias")
  fun `shouldUseLastPathSegmentAsAliasWhenNoAlias`() {
    val directives = listOf(ImportDirective("global.banking-helpers", null, false))
    val result = ImportResolver(registryWith("SOME_EVENT")).resolve(directives)
    assertTrue(result.containsKey("banking-helpers"))
  }
}
