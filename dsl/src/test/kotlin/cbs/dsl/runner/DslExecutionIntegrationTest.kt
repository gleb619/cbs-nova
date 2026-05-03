package cbs.dsl.runner

import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.HelperTypes.HelperInput
import cbs.dsl.api.HelperTypes.HelperOutput
import cbs.dsl.compiler.CompileResult
import cbs.dsl.compiler.DslCompiler
import cbs.dsl.compiler.DslValidator
import cbs.dsl.compiler.InMemoryRulesSource
import cbs.dsl.runtime.EventBuilder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@Disabled
class DslExecutionIntegrationTest {
  private lateinit var registry: cbs.dsl.runtime.DslRegistry
  private lateinit var runner: DslRunner

  @BeforeEach
  fun setUp() {
    val source = InMemoryRulesSource(SCRIPTS)
    val result = DslCompiler(source, DslValidator()).compile()
    assertTrue(
        result is CompileResult.Success,
        "Compile failed: ${(result as? CompileResult.Failure)?.errors}",
    )
    registry = (result as CompileResult.Success).registry

    val kycTx = registry.transactions["KYC_CHECK"]!!
    val debitTx = registry.transactions["DEBIT_ACCOUNT"]!!
    val eventDef =
        EventBuilder("LOAN_DISBURSEMENT").apply {
          parameters {
            required("customerId")
            required("amount")
          }
          context { ctx ->
            ctx["customer"] =
                ctx.helper("FIND_CUSTOMER", mapOf("id" to ctx.eventParameters["customerId"]!!))
          }
          transactions {
            val k = step(kycTx)
            val d = step(debitTx)
            await(k, d)
          }
          finish { ctx, _ -> ctx["done"] = true }
        }
    registry.register(eventDef)
    runner = DslRunner(registry)
  }

  @Test
  @DisplayName("shouldEnrichContextWhenHelperIsCalledInContextBlock")
  fun `shouldEnrichContextWhenHelperIsCalledInContextBlock`() {
    val record = runner.run("LOAN_DISBURSEMENT", mapOf("customerId" to "C1", "amount" to 100))
    assertEquals(mapOf("name" to "Alice"), record.enrichment["customer"])
  }

  @Test
  @DisplayName("shouldExecuteTransactionsWhenEventHasTransactionsBlock")
  fun `shouldExecuteTransactionsWhenEventHasTransactionsBlock`() {
    val record = runner.run("LOAN_DISBURSEMENT", mapOf("customerId" to "C1", "amount" to 100))
    assertEquals(listOf("KYC_CHECK", "DEBIT_ACCOUNT"), record.txResults)
  }

  @Test
  @DisplayName("shouldSetDebitTxIdWhenDebitTransactionExecutes")
  fun `shouldSetDebitTxIdWhenDebitTransactionExecutes`() {
    val record = runner.run("LOAN_DISBURSEMENT", mapOf("customerId" to "C1", "amount" to 100))
    assertEquals("TX-100", record.enrichment["debitTxId"])
  }

  @Test
  @DisplayName("shouldSetKycPassedWhenKycTransactionExecutes")
  fun `shouldSetKycPassedWhenKycTransactionExecutes`() {
    val record = runner.run("LOAN_DISBURSEMENT", mapOf("customerId" to "C1", "amount" to 100))
    assertEquals(true, record.enrichment["kycPassed"])
  }

  @Test
  @DisplayName("shouldCallFinishBlockWhenEventCompletes")
  fun `shouldCallFinishBlockWhenEventCompletes`() {
    val record = runner.run("LOAN_DISBURSEMENT", mapOf("customerId" to "C1", "amount" to 100))
    assertEquals(true, record.enrichment["done"])
  }

  @Test
  @DisplayName("shouldThrowWhenEventCodeNotFound")
  fun `shouldThrowWhenEventCodeNotFound`() {
    val ex = assertFailsWith<IllegalArgumentException> { runner.run("UNKNOWN", emptyMap()) }
    assertTrue(ex.message!!.contains("UNKNOWN"))
  }

  @Test
  @DisplayName("shouldResolveHelperChainWhenHelperCallsAnotherHelper")
  fun `shouldResolveHelperChainWhenHelperCallsAnotherHelper`() {
    val enrichHelper =
        object : HelperDefinition {
          override fun getCode(): String = "ENRICH_CUSTOMER"

          override fun execute(input: HelperInput): HelperOutput {
            val findDef = registry.helpers["FIND_CUSTOMER"]!!

            @Suppress("UNCHECKED_CAST")
            val base =
                findDef
                    .execute(
                        HelperInput(
                            input.params(),
                            input.eventCode() ?: "",
                            input.workflowExecutionId() ?: 0L,
                        )
                    )
                    .value() as Map<String, Any>
            return HelperOutput(base + mapOf("enriched" to true))
          }
        }
    registry.register(enrichHelper)

    val chainEvent =
        EventBuilder("CHAIN_EVENT").apply {
          context { ctx -> ctx["result"] = ctx.helper("ENRICH_CUSTOMER", mapOf("id" to "C1")) }
        }
    registry.register(chainEvent)

    val record = runner.run("CHAIN_EVENT", emptyMap())

    @Suppress("UNCHECKED_CAST") val result = record.enrichment["result"] as Map<String, Any>
    assertTrue(result.containsKey("name"))
    assertTrue(result.containsKey("enriched"))
  }

  companion object {
    private val SCRIPTS =
        mapOf(
            "helpers.helper.kts" to
                """
                helper("FIND_CUSTOMER") {
                    execute { mapOf("name" to "Alice") }
                }
                """
                    .trimIndent(),
            "kyc.transaction.kts" to
                """
                transaction("KYC_CHECK") {
                    execute { ctx -> ctx["kycPassed"] = true }
                }
                """
                    .trimIndent(),
            "debit.transaction.kts" to
                """
                transaction("DEBIT_ACCOUNT") {
                    execute { ctx -> ctx["debitTxId"] = "TX-${'$'}{ctx.eventParameters["amount"]}" }
                }
                """
                    .trimIndent(),
        )
  }
}
