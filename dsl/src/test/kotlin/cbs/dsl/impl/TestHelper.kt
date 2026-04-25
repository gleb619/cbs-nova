package cbs.dsl.impl

import cbs.dsl.api.DslComponent
import cbs.dsl.api.DslImplType
import cbs.dsl.api.HelperFunction
import cbs.dsl.api.HelperFunction.HelperArg
import cbs.dsl.api.HelperFunction.HelperResult
import cbs.dsl.impl.TestHelper.TestArg
import cbs.dsl.impl.TestHelper.TestResult

@DslComponent(code = "TEST_HELPER", type = DslImplType.HELPER)
class TestHelper : HelperFunction<TestArg, TestResult> {

  data class TestArg(val value: String) : HelperArg

  data class TestResult(val value: String) : HelperResult

  override fun execute(input: TestArg): TestResult {
    return TestResult(value = input.value + "-test")
  }

}