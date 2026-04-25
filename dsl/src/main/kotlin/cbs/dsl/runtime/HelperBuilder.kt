package cbs.dsl.runtime

import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.HelperTypes.HelperInput
import cbs.dsl.api.HelperTypes.HelperOutput
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.context.BaseContext
import cbs.dsl.api.context.HelperContext
import java.util.function.Consumer

class HelperBuilder(val helperCode: String) : HelperDefinition {
  private var _name: String? = null

  private val _parameters = mutableListOf<ParameterDefinition>()
  override fun getParameters(): List<ParameterDefinition> = _parameters.toList()

  private var _contextBlock: Consumer<HelperContext> = Consumer { }
  override fun getContextBlock(): Consumer<HelperContext> = _contextBlock

  override fun getCode(): String = helperCode

  override fun getName(): String? = _name

  private var executeBlock: ((HelperContext) -> Any)? = null

  fun name(value: String) {
    _name = value
  }

  fun parameters(block: ParametersScope.() -> Unit) {
    _parameters += ParametersScope().apply(block).definitions
  }

  fun context(block: (HelperContext) -> Unit) {
    _contextBlock = Consumer { block(it) }
  }

  fun execute(block: (HelperContext) -> Any) {
    executeBlock = block
  }

  override fun execute(input: HelperInput): HelperOutput {
    val ctx = HelperContext.helperBuilder()
          .eventCode(input.eventCode() ?: "")
          .workflowExecutionId(input.workflowExecutionId() ?: 0L)
          .performedBy("")
          .dslVersion("")
          .params(input.params())
          .build()
    _contextBlock.accept(ctx)

    val result = executeBlock?.invoke(ctx) ?: error("Helper '$helperCode' has no execute block defined")

    return HelperOutput(result)
  }

  fun executeWithResolver(
      params: Map<String, Any>,
      baseCtx: BaseContext,
      resolver: (String, Map<String, Any>) -> Any,
  ): Any {
    val ctx = HelperContext.helperBuilder()
      .eventCode(baseCtx.eventCode)
      .workflowExecutionId(baseCtx.workflowExecutionId)
      .performedBy(baseCtx.performedBy)
      .dslVersion(baseCtx.dslVersion)
      .params(params)
      .build()

    _contextBlock.accept(ctx)

    val result = executeBlock?.invoke(ctx) ?: error("Helper '$helperCode' has no execute block defined")

    return result
  }
}

fun helper(code: String, block: HelperBuilder.() -> Unit): HelperDefinition =
    HelperBuilder(code).apply(block)
