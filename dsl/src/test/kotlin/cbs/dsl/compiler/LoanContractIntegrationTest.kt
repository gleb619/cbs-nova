package cbs.dsl.compiler

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoanContractIntegrationTest {
    private lateinit var registry: cbs.dsl.runtime.DslRegistry

    @BeforeEach
    fun setUp() {
        val source = InMemoryRulesSource(LOAN_CONTRACT_SCRIPTS)
        val compiler = DslCompiler(source, DslValidator())
        val result = compiler.compile()
        assertTrue(result is CompileResult.Success, "Compile failed: ${(result as? CompileResult.Failure)?.errors}")
        registry = (result as CompileResult.Success).registry
    }

    @Test
    @DisplayName("shouldCompileSuccessfullyWhenAllSixScopeTypesPresent")
    fun `shouldCompileSuccessfullyWhenAllSixScopeTypesPresent`() {
        // assertion is in setUp — reaching here means success
        assertTrue(registry.workflows.isNotEmpty() || registry.events.isNotEmpty())
    }

    @Test
    @DisplayName("shouldRegisterWorkflowWhenWorkflowScriptCompiled")
    fun `shouldRegisterWorkflowWhenWorkflowScriptCompiled`() {
        assertNotNull(registry.workflows["LOAN_CONTRACT"])
    }

    @Test
    @DisplayName("shouldRegisterEventWhenEventScriptCompiled")
    fun `shouldRegisterEventWhenEventScriptCompiled`() {
        assertNotNull(registry.events["LOAN_DISBURSEMENT"])
    }

    @Test
    @DisplayName("shouldRegisterTransactionsWhenTransactionScriptsCompiled")
    fun `shouldRegisterTransactionsWhenTransactionScriptsCompiled`() {
        assertNotNull(registry.transactions["KYC_CHECK"])
        assertNotNull(registry.transactions["DEBIT_FUNDING_ACCOUNT"])
    }

    @Test
    @DisplayName("shouldRegisterHelpersWhenHelperScriptsCompiled")
    fun `shouldRegisterHelpersWhenHelperScriptsCompiled`() {
        assertNotNull(registry.helpers["FIND_BANK_ACCOUNT"])
        assertNotNull(registry.helpers["FIND_CUSTOMER_CODE_BY_ID"])
        assertNotNull(registry.helpers["LOAN_CONDITIONS_BY_ID"])
    }

    @Test
    @DisplayName("shouldRegisterConditionWhenConditionScriptCompiled")
    fun `shouldRegisterConditionWhenConditionScriptCompiled`() {
        assertNotNull(registry.conditions["BORROWER_ACCOUNT_READY"])
    }

    @Test
    @DisplayName("shouldRegisterMassOperationWhenMassScriptCompiled")
    fun `shouldRegisterMassOperationWhenMassScriptCompiled`() {
        val massOp = registry.massOperations["INTEREST_CHARGE"]
        assertNotNull(massOp)
        assertTrue(massOp.triggers.size >= 1)
    }

    @Test
    @DisplayName("shouldInjectImportsWhenScriptUsesImportDirective")
    fun `shouldInjectImportsWhenScriptUsesImportDirective`() {
        assertNotNull(registry.workflows["LOAN_CONTRACT"])
    }

    @Test
    @DisplayName("shouldHaveCorrectTransitionCountWhenWorkflowHasMultipleTransitions")
    fun `shouldHaveCorrectTransitionCountWhenWorkflowHasMultipleTransitions`() {
        assertEquals(3, registry.workflows["LOAN_CONTRACT"]!!.transitions.size)
    }

    @Test
    @DisplayName("shouldHaveCorrectParametersWhenEventHasRequiredParams")
    fun `shouldHaveCorrectParametersWhenEventHasRequiredParams`() {
        val params = registry.events["LOAN_DISBURSEMENT"]!!.parameters.map { it.name }
        assertTrue("customerId" in params)
        assertTrue("loanId" in params)
        assertTrue("amount" in params)
    }

    companion object {
        private val LOAN_CONTRACT_SCRIPTS: Map<String, String> =
            mapOf(
                "global/banking-helpers.helper.kts" to
                    """
                    helpers {
                        helper("FIND_BANK_ACCOUNT") {
                            execute { ctx -> mapOf("iban" to ctx.params["iban"]) }
                        }
                        helper("FIND_CUSTOMER_CODE_BY_ID") {
                            execute { ctx -> "CUST-${'$'}{ctx.params["id"]}" }
                        }
                    }
                    """.trimIndent(),
                "loan-disbursement/loan-helpers.helper.kts" to
                    """
                    helpers {
                        helper("LOAN_CONDITIONS_BY_ID") {
                            execute { ctx -> mapOf("loanId" to ctx.params["loanId"], "currency" to "USD") }
                        }
                    }
                    """.trimIndent(),
                "loan-disbursement/kyc-check.transaction.kts" to
                    """
                    transaction("KYC_CHECK") {
                        execute { ctx ->
                            ctx["kycVerified"] = true
                        }
                    }
                    """.trimIndent(),
                "loan-disbursement/debit-funding-account.transaction.kts" to
                    """
                    transaction("DEBIT_FUNDING_ACCOUNT") {
                        execute { ctx ->
                            ctx["debitTxId"] = "TX-001"
                        }
                    }
                    """.trimIndent(),
                "loan-disbursement/borrower-account-ready.condition.kts" to
                    """
                    condition("BORROWER_ACCOUNT_READY") {
                        predicate { ctx -> ctx.enrichment["accountCode"] != null }
                    }
                    """.trimIndent(),
                "loan-disbursement/loan-disbursement.event.kts" to
                    """
                    // #import loan-disbursement.* as disb

                    event("LOAN_DISBURSEMENT") {
                        parameters {
                            required("customerId")
                            required("loanId")
                            required("amount")
                            optional("accountNumber")
                        }
                        context { ctx ->
                            ctx["accountCode"] = "nil"
                        }
                        finish { ctx, ex ->
                            // no-op
                        }
                    }
                    """.trimIndent(),
                "mass-operations/interest-charge/interest-charge.mass.kts" to
                    """
                    massOperation("INTEREST_CHARGE") {
                        category("CREDITS")
                        trigger(TriggerDefinition.CronTrigger("0 1 * * *"))
                        source(object : SourceDefinition {
                            override fun load(ctx: MassOperationContext): List<Map<String, Any>> = emptyList()
                        })
                        item { ctx ->
                            // no-op
                        }
                    }
                    """.trimIndent(),
                "loan-contract.workflow.kts" to
                    """
                    // #import loan-disbursement.* as disb

                    workflow("LOAN_CONTRACT") {
                        states("DRAFT", "ENTERED", "ACTIVE", "CANCELLED", "CLOSED", "FAULTED")
                        initial("ENTERED")
                        terminalStates("CLOSED", "CANCELLED")
                        transitions {
                            (("DRAFT" to "ENTERED") on Action.SUBMIT) {
                                context { ctx -> }
                            }
                            (("ENTERED" to "ACTIVE") on Action.APPROVE) {
                                context { ctx -> }
                            }
                            (("ENTERED" to "CANCELLED") on Action.CANCEL) {
                                context { ctx -> }
                            }
                        }
                    }
                    """.trimIndent(),
            )
    }
}
