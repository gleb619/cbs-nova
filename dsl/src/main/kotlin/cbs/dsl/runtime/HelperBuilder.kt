package cbs.dsl.runtime

import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.HelperTypes.HelperInput
import cbs.dsl.api.HelperTypes.HelperOutput
import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.context.BaseContext
import cbs.dsl.api.context.HelperContext

class HelperBuilder(override val code: String) : HelperDefinition {
  private var _name: String? = null
  override val name: String?
    get() = _name

  private val _parameters = mutableListOf<ParameterDefinition>()
  override val parameters: List<ParameterDefinition>
    get() = _parameters.toList()

  private var _contextBlock: (HelperContext) -> Unit = {}
  override val contextBlock: (HelperContext) -> Unit
    get() = _contextBlock

  private var executeBlock: ((HelperContext) -> Any)? = null

  fun name(value: String) {
    _name = value
  }

  fun parameters(block: ParametersScope.() -> Unit) {
    _parameters += ParametersScope().apply(block).definitions
  }

  fun context(block: (HelperContext) -> Unit) {
    _contextBlock = block
  }

  fun execute(block: (HelperContext) -> Any) {
    executeBlock = block
  }

  override fun execute(input: HelperInput): HelperOutput {
    val ctx =
        HelperContext(
            eventCode = input.eventCode() ?: "",
            workflowExecutionId = input.workflowExecutionId() ?: 0L,
            performedBy = "",
            dslVersion = "",
            params = input.params(),
        )
    _contextBlock(ctx)

    val result = executeBlock?.invoke(ctx) ?: error("Helper '$code' has no execute block defined")

    return HelperOutput(result)
  }

  fun executeWithResolver(
      params: Map<String, Any>,
      baseCtx: BaseContext,
      resolver: (String, Map<String, Any>) -> Any,
  ): Any {
    val ctx =
        object :
            HelperContext(
                eventCode = baseCtx.eventCode,
                workflowExecutionId = baseCtx.workflowExecutionId,
                performedBy = baseCtx.performedBy,
                dslVersion = baseCtx.dslVersion,
                params = params,
            ) {
          override fun helper(name: String, params: Map<String, Any>): Any = resolver(name, params)
        }

    _contextBlock(ctx)

    val result = executeBlock?.invoke(ctx) ?: error("Helper '$code' has no execute block defined")

    return result
  }
}

fun helper(code: String, block: HelperBuilder.() -> Unit): HelperDefinition =
    HelperBuilder(code).apply(block)
