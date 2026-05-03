package cbs.dsl.runtime

import cbs.dsl.api.ParameterDefinition

class ParametersScope {
  internal val definitions: MutableList<ParameterDefinition> = mutableListOf()

  fun required(name: String) {
    definitions += ParameterDefinition(name, true)
  }

  fun optional(name: String) {
    definitions += ParameterDefinition(name, false)
  }
}
