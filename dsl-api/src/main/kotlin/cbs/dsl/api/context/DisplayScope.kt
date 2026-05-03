package cbs.dsl.api.context

class DisplayScope {
  val labels: MutableList<Pair<String, Any?>> = mutableListOf()

  fun label(key: String, value: Any?) {
    labels += key to value
  }
}
