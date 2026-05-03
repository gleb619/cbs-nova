package cbs.dsl.impl

import cbs.dsl.api.DslComponent
import cbs.dsl.api.DslImplType
import cbs.dsl.api.TransactionFunction
import cbs.dsl.api.TransactionFunction.TransactionArg
import cbs.dsl.api.TransactionFunction.TransactionResult
import cbs.dsl.impl.TestTransaction.TestArg
import cbs.dsl.impl.TestTransaction.TestResult

@DslComponent(code = "TEST_TRANSACTION", type = DslImplType.TRANSACTION)
class TestTransaction : TransactionFunction<TestArg, TestResult> {

  data class TestArg(val value: String) : TransactionArg

  data class TestResult(val value: String) : TransactionResult

  override fun execute(input: TestArg): TestResult {
    return TestResult(input.value + "-tr")
  }

}