package cbs.dsl.runner

import cbs.dsl.api.EventDefinition
import cbs.dsl.compiler.CompileResult
import cbs.dsl.compiler.DslCompiler
import cbs.dsl.compiler.DslValidator
import cbs.dsl.compiler.InMemoryRulesSource
import cbs.dsl.compiler.SampleLoader
import cbs.dsl.impl.ImplRegistry
import cbs.dsl.impl.TestHelper
import cbs.dsl.impl.TestTransaction
import cbs.dsl.impl.populateFrom
import cbs.dsl.runtime.EventBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DslImplDispatchTest {
    private lateinit var registry: cbs.dsl.runtime.DslRegistry
    private lateinit var runner: DslRunner
    private lateinit var implRegistry: ImplRegistry

    @BeforeEach
    fun setUp() {
        // Load only transaction and helper files (not the event file which uses #import)
        val samples =
            mapOf(
                "loan-disbursement/kyc-check.transaction.kts" to
                    readFile("samples/loan-disbursement/kyc-check.transaction.kts"),
                "loan-disbursement/credit-scoring.transaction.kts" to
                    readFile("samples/loan-disbursement/credit-scoring.transaction.kts"),
                "loan-disbursement/debit-funding-account.transaction.kts" to
                    readFile("samples/loan-disbursement/debit-funding-account.transaction.kts"),
                "loan-disbursement/credit-borrower-account.transaction.kts" to
                    readFile("samples/loan-disbursement/credit-borrower-account.transaction.kts"),
                "loan-disbursement/loan-helpers.helper.kts" to
                    readFile("samples/loan-disbursement/loan-helpers.helper.kts"),
                "global/banking-helpers.helper.kts" to
                    readFile("samples/global/banking-helpers.helper.kts"),
            )
        val source = InMemoryRulesSource(samples)
        val result = DslCompiler(source, DslValidator()).compile()
        assertTrue(result is CompileResult.Success, "Compile failed: ${(result as? CompileResult.Failure)?.errors}")
        registry = (result as CompileResult.Success).registry

        // Get compiled transactions
        val kycTx = registry.transactions["KYC_CHECK"]!!
        val scoringTx = registry.transactions["CREDIT_SCORING"]!!
        val debitTx = registry.transactions["DEBIT_FUNDING_ACCOUNT"]!!
        val creditTx = registry.transactions["CREDIT_BORROWER_ACCOUNT"]!!

        // Create event definition manually (the .kts event file uses #import which fails in pass 1)
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
                finish { ctx, ex ->
                    ctx["disbursed"] = ex == null
                }
            }
        registry.register(eventDef)

        implRegistry =
            ImplRegistry().apply {
                populateFrom(registry)
                // Override with TestXxx implementations
                register(
                    TestTransaction(
                        code = "KYC_CHECK",
                        name = "TestKycCheck",
                        executeBlock = { ctx ->
                            ctx["kycVerified"] = true
                            ctx["_kycImpl"] = "TestTransaction"
                        },
                    ),
                )
                register(
                    TestHelper(
                        code = "FIND_CUSTOMER_CODE_BY_ID",
                        name = "TestFindCustomerCodeById",
                        executeBlock = { params -> "CUST-${params["id"]}-via-TestHelper" },
                    ),
                )
                register(
                    TestHelper(
                        code = "LOAN_CONDITIONS_BY_ID",
                        name = "TestLoanConditionsById",
                        executeBlock = { params -> mapOf("loanId" to params["loanId"], "currency" to "USD") },
                    ),
                )
            }

        runner = DslRunner(registry, implRegistry)
    }

    @Test
    @DisplayName("shouldCallTestHelperWhenHelperResolvedByName")
    fun `shouldCallTestHelperWhenHelperResolvedByName`() {
        val record =
            runner.run(
                "LOAN_DISBURSEMENT",
                mapOf("customerId" to "C1", "loanId" to "L1", "amount" to 1000),
            )

        val customerCode = record.enrichment["customerCode"] as? String
        assertTrue(
            customerCode?.endsWith("-via-TestHelper") == true,
            "Expected customerCode to end with '-via-TestHelper', but was: $customerCode",
        )
    }

    @Test
    @DisplayName("shouldCallTestTransactionWhenTransactionResolvedByName")
    fun `shouldCallTestTransactionWhenTransactionResolvedByName`() {
        val record =
            runner.run(
                "LOAN_DISBURSEMENT",
                mapOf("customerId" to "C1", "loanId" to "L1", "amount" to 1000),
            )

        assertEquals("TestTransaction", record.enrichment["_kycImpl"])
    }

    @Test
    @DisplayName("shouldCallTestHelperForLoanConditionsWhenResolvedByName")
    fun `shouldCallTestHelperForLoanConditionsWhenResolvedByName`() {
        val record =
            runner.run(
                "LOAN_DISBURSEMENT",
                mapOf("customerId" to "C1", "loanId" to "L1", "amount" to 1000),
            )

        val loanConditions = record.enrichment["loanConditions"]
        assertTrue(loanConditions is Map<*, *>, "Expected loanConditions to be a Map, but was: ${loanConditions?.javaClass}")
    }

    @Test
    @DisplayName("shouldExecuteAllFourTransactionsWhenRunningFullScenario")
    fun `shouldExecuteAllFourTransactionsWhenRunningFullScenario`() {
        val record =
            runner.run(
                "LOAN_DISBURSEMENT",
                mapOf("customerId" to "C1", "loanId" to "L1", "amount" to 1000),
            )

        assertEquals(
            listOf("KYC_CHECK", "CREDIT_SCORING", "DEBIT_FUNDING_ACCOUNT", "CREDIT_BORROWER_ACCOUNT"),
            record.txResults,
        )
    }

    @Test
    @DisplayName("shouldSetDisbursedTrueWhenFinishBlockRuns")
    fun `shouldSetDisbursedTrueWhenFinishBlockRuns`() {
        val record =
            runner.run(
                "LOAN_DISBURSEMENT",
                mapOf("customerId" to "C1", "loanId" to "L1", "amount" to 1000),
            )

        assertEquals(true, record.enrichment["disbursed"])
    }

    private fun readFile(path: String): String =
        javaClass.classLoader.getResource(path)?.readText()
            ?: error("Resource not found: $path")
}
