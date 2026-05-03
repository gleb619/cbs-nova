package cbs.dsl.api

data class Signal(
  val source: String,
  val type: SignalType,
  val payload: Map<String, Any>,
) {
  companion object {
    fun partial(source: String, payload: Map<String, Any>): Signal =
      Signal(source, SignalType.PARTIAL, payload)

    fun completed(source: String, payload: Map<String, Any>): Signal =
      Signal(source, SignalType.COMPLETED, payload)
  }
}

enum class SignalType {
  PARTIAL,
  COMPLETED,
}
