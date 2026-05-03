package cbs.dsl.impl

import cbs.dsl.api.DslImpl
import cbs.dsl.api.DslImplType
import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.HelperInput
import cbs.dsl.api.HelperOutput
import cbs.dsl.runtime.AnyHelperOutput
import cbs.dsl.runtime.MapHelperInput

@DslImpl(code = "LOAN_CONDITIONS_BY_ID", type = DslImplType.HELPER)
class LoanConditionsByIdHelper : HelperDefinition {
  override val code = "LOAN_CONDITIONS_BY_ID"
  override val name = "TestLoanConditionsById"

  override fun execute(input: HelperInput): HelperOutput {
    val mapInput = input as MapHelperInput
    val result = mapOf("loanId" to mapInput.params["loanId"], "currency" to "USD")
    return AnyHelperOutput(result)
  }
}
