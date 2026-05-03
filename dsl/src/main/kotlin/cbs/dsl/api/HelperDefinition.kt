package cbs.dsl.api

interface HelperDefinition : HelperFunction<HelperInput, HelperOutput> {
  val code: String
}
