package cbs.dsl.api

interface HelperFunction<I : HelperInput, O : HelperOutput> {
  fun execute(input: I): O
}
