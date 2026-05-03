package cbs.dsl.api

import lombok.Builder

@Builder
data class ParameterDefinition(val name: String?, val required: Boolean)