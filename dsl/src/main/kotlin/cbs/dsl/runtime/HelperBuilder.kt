package cbs.dsl.runtime

import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.HelperInput
import cbs.dsl.api.HelperOutput
import cbs.dsl.api.context.BaseContext
import cbs.dsl.api.context.HelperContext

data class MapHelperInput(
    val params: Map<String, Any>,
    val baseContext: BaseContext,
) : HelperInput

data class AnyHelperOutput(
    val value: Any,
) : HelperOutput

class HelperBuilder(
    override val code: String,
) : HelperDefinition {
    private var _name: String? = null
    private var executeBlock: ((HelperContext) -> Any)? = null

    override val name: String? get() = _name

    fun name(value: String) {
        _name = value
    }

    fun execute(block: (HelperContext) -> Any) {
        executeBlock = block
    }

    override fun execute(input: HelperInput): HelperOutput {
        val mapInput =
            input as? MapHelperInput
                ?: error("Helper input must be MapHelperInput")

        val ctx =
            HelperContext(
                eventCode = mapInput.baseContext.eventCode,
                workflowExecutionId = mapInput.baseContext.workflowExecutionId,
                performedBy = mapInput.baseContext.performedBy,
                dslVersion = mapInput.baseContext.dslVersion,
                params = mapInput.params,
            )

        val result =
            executeBlock?.invoke(ctx)
                ?: error("Helper '$code' has no execute block defined")

        return AnyHelperOutput(result)
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
                override fun helper(
                    name: String,
                    params: Map<String, Any>,
                ): Any = resolver(name, params)
            }

        val result =
            executeBlock?.invoke(ctx)
                ?: error("Helper '$code' has no execute block defined")

        return result
    }
}

fun helper(
    code: String,
    block: HelperBuilder.() -> Unit,
): HelperDefinition = HelperBuilder(code).apply(block)
