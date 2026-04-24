package cbs.dsl.impl

import cbs.dsl.api.DslComponent
import cbs.dsl.api.DslImplType
import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.HelperTypes.HelperInput
import cbs.dsl.api.HelperTypes.HelperOutput

@DslComponent(code = "LOAN_CONDITIONS_BY_ID", type = DslImplType.HELPER)
class LoanConditionsByIdHelper : HelperDefinition {
  override val code = "LOAN_CONDITIONS_BY_ID"
  override val name = "TestLoanConditionsById"

  override fun execute(input: HelperInput): HelperOutput {
    val result = mapOf("loanId" to input.params()["loanId"], "currency" to "USD")
    return HelperOutput(result)
  }
}
