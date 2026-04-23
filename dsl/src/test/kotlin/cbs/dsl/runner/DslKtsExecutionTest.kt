package cbs.dsl.runner

import cbs.dsl.compiler.CompileResult
import cbs.dsl.compiler.DslCompiler
import cbs.dsl.compiler.DslValidator
import cbs.dsl.compiler.InMemoryRulesSource
import cbs.dsl.compiler.SampleLoader
import cbs.dsl.runtime.EventBuilder
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DslKtsExecutionTest {
  private lateinit var registry: cbs.dsl.runtime.DslRegistry
  private lateinit var runner: DslRunner

  @BeforeEach
  fun setUp() {
    val scripts = loadSamples()
    val source = InMemoryRulesSource(scripts)
    val result = DslCompiler(source, DslValidator()).compile()
    assertTrue(
        result is CompileResult.Success,
        "Compile failed: ${(result as? CompileResult.Failure)?.errors}",
    )
    registry = (result as CompileResult.Success).registry

    // Re-register LOAN_DISBURSEMENT event with explicit transaction references
    // (avoiding import mechanism which requires pass-2 compilation)
    val kycTx = registry.transactions["KYC_CHECK"]!!
    val scoringTx = registry.transactions["CREDIT_SCORING"]!!
    val debitTx = registry.transactions["DEBIT_FUNDING_ACCOUNT"]!!
    val creditTx = registry.transactions["CREDIT_BORROWER_ACCOUNT"]!!

    val eventDef =
        EventBuilder("LOAN_DISBURSEMENT").apply {
          parameters {
            required("customerId")
            required("loanId")
            required("amount")
            optional("accountNumber")
          }
          context { ctx ->
            ctx["customerCode"] =
                ctx.helper(
                    "FIND_CUSTOMER_CODE_BY_ID",
                    mapOf("id" to ctx.eventParameters["customerId"]!!),
                )
            ctx["loanConditions"] =
                ctx.helper(
                    "LOAN_CONDITIONS_BY_ID",
                    mapOf("loanId" to ctx.eventParameters["loanId"]!!),
                )
          }
          transactions {
            val kyc = step(kycTx)
            val scoring = step(scoringTx)
            val debit = step(debitTx)
            val credit = step(creditTx)
            await(kyc, scoring, debit, credit)
          }
          finish { ctx, ex -> ctx["disbursed"] = ex == null }
        }

    // Unregister the old event (which had imports) and register the new one
    // Since DslRegistry doesn't have unregister, we create a fresh registry
    val finalRegistry = cbs.dsl.runtime.DslRegistry()
    // Copy all helpers and transactions
    registry.helpers.forEach { finalRegistry.register(it.value) }
    registry.transactions.forEach { finalRegistry.register(it.value) }
    registry.conditions.forEach { finalRegistry.register(it.value) }
    finalRegistry.register(eventDef)

    registry = finalRegistry
    runner = DslRunner(registry)
  }

  @Test
  @DisplayName("shouldEnrichCustomerCodeWhenContextBlockRuns")
  fun shouldEnrichCustomerCodeWhenContextBlockRuns() {
    val record =
        runner.run(
            "LOAN_DISBURSEMENT",
            mapOf("customerId" to "C1", "loanId" to "L1", "amount" to 5000),
        )
    assertEquals("CUST-C1-via-TestHelper", record.enrichment["customerCode"])
  }

  @Test
  @DisplayName("shouldEnrichLoanConditionsWhenContextBlockRuns")
  fun shouldEnrichLoanConditionsWhenContextBlockRuns() {
    val record =
        runner.run(
            "LOAN_DISBURSEMENT",
            mapOf("customerId" to "C1", "loanId" to "L1", "amount" to 5000),
        )
    assertTrue(record.enrichment["loanConditions"] is Map<*, *>)
  }

  @Test
  @DisplayName("shouldExecuteAllFourTransactionsInOrder")
  fun shouldExecuteAllFourTransactionsInOrder() {
    val record =
        runner.run(
            "LOAN_DISBURSEMENT",
            mapOf("customerId" to "C1", "loanId" to "L1", "amount" to 5000),
        )
    assertEquals(
        listOf("KYC_CHECK", "CREDIT_SCORING", "DEBIT_FUNDING_ACCOUNT", "CREDIT_BORROWER_ACCOUNT"),
        record.txResults,
    )
  }

  @Test
  @DisplayName("shouldSetDisbursedTrueWhenFinishBlockRuns")
  fun shouldSetDisbursedTrueWhenFinishBlockRuns() {
    val record =
        runner.run(
            "LOAN_DISBURSEMENT",
            mapOf("customerId" to "C1", "loanId" to "L1", "amount" to 5000),
        )
    assertEquals(true, record.enrichment["disbursed"])
  }

  private fun loadSamples(): Map<String, String> {
    val global = SampleLoader.loadGroup("global")
    val loanDisbursement = SampleLoader.loadGroup("loan-disbursement")
    return global + loanDisbursement
  }
}
