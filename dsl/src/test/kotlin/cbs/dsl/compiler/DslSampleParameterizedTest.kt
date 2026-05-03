package cbs.dsl.compiler

import cbs.dsl.runtime.DslRegistry
import java.util.stream.Stream
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class DslSampleParameterizedTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("scenarios")
  @DisplayName("shouldCompileSuccessfullyWhenScenarioIsValid")
  fun `shouldCompileSuccessfullyWhenScenarioIsValid`(
      name: String,
      scripts: Map<String, String>,
      assertions: (DslRegistry) -> Unit,
  ) {
    val result = DslCompiler(InMemoryRulesSource(scripts), DslValidator()).compile()
    assertTrue(
        result is CompileResult.Success,
        "Compile failed for '$name': ${(result as? CompileResult.Failure)?.errors}",
    )
    assertions((result as CompileResult.Success).registry)
  }

  companion object {
    @JvmStatic
    fun scenarios(): Stream<Arguments> =
        Stream.of(
            Arguments.of(
                "banking-helpers-only",
                SampleLoader.loadGroup("global/banking-helpers"),
                { reg: DslRegistry ->
                  assertTrue(reg.helpers.containsKey("FIND_BANK_ACCOUNT"))
                  assertTrue(reg.helpers.containsKey("FIND_CUSTOMER_CODE_BY_ID"))
                },
            ),
            Arguments.of(
                "kyc-check-transaction",
                SampleLoader.loadGroup("loan-disbursement/kyc-check"),
                { reg: DslRegistry -> assertTrue(reg.transactions.containsKey("KYC_CHECK")) },
            ),
            Arguments.of(
                "debit-with-preview-and-rollback",
                SampleLoader.loadGroup("loan-disbursement/credit-scoring"),
                { reg: DslRegistry -> assertTrue(reg.transactions.containsKey("CREDIT_SCORING")) },
            ),
            Arguments.of(
                "condition-only",
                SampleLoader.loadGroup("loan-disbursement/borrower-account-ready"),
                { reg: DslRegistry ->
                  assertTrue(reg.conditions.containsKey("BORROWER_ACCOUNT_READY"))
                },
            ),
            Arguments.of(
                "event-with-context-and-finish",
                SampleLoader.loadGroup("loan-disbursement/loan-disbursement"),
                { reg: DslRegistry ->
                  val params = reg.events["LOAN_DISBURSEMENT"]!!.parameters.map { it.name }
                  assertTrue("customerId" in params)
                  assertTrue("loanId" in params)
                },
            ),
            Arguments.of(
                "event-with-transactions-block",
                mapOf(
                    "tx.transaction.kts" to
                        """
                        transaction("INLINE_TX") {
                            execute { ctx -> ctx["done"] = true }
                        }
                        """
                            .trimIndent(),
                    "ev.event.kts" to
                        """
                        event("INLINE_EVENT") {
                            transactions {
                                // transactions block present
                            }
                        }
                        """
                            .trimIndent(),
                ),
                { reg: DslRegistry ->
                  assertTrue(reg.events["INLINE_EVENT"]!!.transactionsBlock != null)
                },
            ),
            Arguments.of(
                "workflow-inferred-states",
                mapOf(
                    "simple.workflow.kts" to
                        """
                        workflow("SIMPLE_WF") {
                            transitions {
                                (("OPEN" to "CLOSED") on Action.SUBMIT) {
                                    context { ctx -> }
                                }
                            }
                        }
                        """
                            .trimIndent()
                ),
                { reg: DslRegistry ->
                  val wf = reg.workflows["SIMPLE_WF"]!!
                  assertTrue(wf.states.size == 2)
                },
            ),
            Arguments.of(
                "workflow-explicit-states",
                SampleLoader.loadGroup("loan-contract"),
                { reg: DslRegistry ->
                  val wf = reg.workflows["LOAN_CONTRACT"]!!
                  assertTrue(wf.transitions.size == 3)
                  assertTrue(wf.states.contains("DRAFT"))
                },
            ),
            Arguments.of(
                "mass-op-cron-trigger",
                SampleLoader.loadGroup("mass-operations/interest-charge"),
                { reg: DslRegistry ->
                  val massOp = reg.massOperations["INTEREST_CHARGE"]!!
                  assertTrue(
                      massOp.triggers.any { it is cbs.dsl.api.TriggerDefinition.CronTrigger }
                  )
                },
            ),
            Arguments.of(
                "mass-op-signal-trigger",
                mapOf(
                    "signal.mass.kts" to
                        """
                        massOperation("SIGNAL_OP") {
                            category("CREDITS")
                            trigger(TriggerDefinition.SignalTrigger(Signal.completed("INTEREST_CHARGE", emptyMap())))
                            source(object : SourceDefinition {
                                override fun load(ctx: MassOperationContext): List<Map<String, Any>> = emptyList()
                            })
                            item { ctx -> }
                        }
                        """
                            .trimIndent()
                ),
                { reg: DslRegistry ->
                  val massOp = reg.massOperations["SIGNAL_OP"]!!
                  assertTrue(
                      massOp.triggers.any { it is cbs.dsl.api.TriggerDefinition.SignalTrigger }
                  )
                },
            ),
            Arguments.of(
                "chained-helpers",
                mapOf(
                    "chained.helper.kts" to
                        """
                        helpers {
                            helper("BASE_HELPER") {
                                execute { ctx -> "base-${'$'}{ctx.params["id"]}" }
                            }
                            helper("CHAINED_HELPER") {
                                execute { ctx -> "chained-${'$'}{ctx.params["id"]}" }
                            }
                        }
                        """
                            .trimIndent()
                ),
                { reg: DslRegistry -> assertTrue(reg.helpers.containsKey("CHAINED_HELPER")) },
            ),
            Arguments.of(
                "multi-branch-condition",
                mapOf(
                    "branching.event.kts" to
                        """
                        event("BRANCHING_EVENT") {
                            transactions {
                                step {
                                    `when`({ true }) then {
                                        transaction(object : cbs.dsl.api.TransactionDefinition {
                                            override val code = "BRANCH_A"
                                            override fun preview(ctx: cbs.dsl.api.context.TransactionContext) {}
                                            override fun execute(ctx: cbs.dsl.api.context.TransactionContext) {}
                                            override fun rollback(ctx: cbs.dsl.api.context.TransactionContext) {}
                                        })
                                    } otherwise {
                                        transaction(object : cbs.dsl.api.TransactionDefinition {
                                            override val code = "BRANCH_B"
                                            override fun preview(ctx: cbs.dsl.api.context.TransactionContext) {}
                                            override fun execute(ctx: cbs.dsl.api.context.TransactionContext) {}
                                            override fun rollback(ctx: cbs.dsl.api.context.TransactionContext) {}
                                        })
                                    }
                                }
                            }
                        }
                        """
                            .trimIndent()
                ),
                { reg: DslRegistry ->
                  assertTrue(reg.events["BRANCHING_EVENT"]!!.transactionsBlock != null)
                },
            ),
            Arguments.of(
                "full-loan-contract",
                SampleLoader.loadAll(),
                { reg: DslRegistry ->
                  assertTrue(reg.workflows.isNotEmpty())
                  assertTrue(reg.events.isNotEmpty())
                  assertTrue(reg.transactions.isNotEmpty())
                  assertTrue(reg.helpers.isNotEmpty())
                  assertTrue(reg.conditions.isNotEmpty())
                  assertTrue(reg.massOperations.isNotEmpty())
                },
            ),
        )
  }
}
