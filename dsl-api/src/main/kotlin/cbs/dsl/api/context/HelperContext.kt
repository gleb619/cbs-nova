package cbs.dsl.api.context

open class HelperContext(
  eventCode: String,
  workflowExecutionId: Long,
  performedBy: String,
  dslVersion: String,
  val params: Map<String, Any>,
) : BaseContext(eventCode, workflowExecutionId, performedBy, dslVersion) {
  open fun helper(name: String, params: Map<String, Any>): Any = Unit

  open fun <T : Any> resolve(clazz: kotlin.reflect.KClass<T>): T =
    error("HelperContext.resolve not implemented")
}