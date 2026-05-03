package cbs.dsl.impl

import cbs.dsl.api.ConditionFunction
import cbs.dsl.api.ConditionFunction.ConditionArg
import cbs.dsl.api.ConditionFunction.ConditionResult
import cbs.dsl.api.DslComponent
import cbs.dsl.api.DslImplType
import cbs.dsl.impl.TestCondition.TestArg
import cbs.dsl.impl.TestCondition.TestResult

@DslComponent(code = "TEST_CONDITION", type = DslImplType.CONDITION)
class TestCondition : ConditionFunction<TestArg, TestResult> {

  data class TestArg(val value: Int) : ConditionArg

  data class TestResult(private val value: Boolean) : ConditionResult {
    override fun getValue() = value
  }

  override fun evaluate(input: TestArg): TestResult {
    return TestResult(input.value >= 5)
  }

}