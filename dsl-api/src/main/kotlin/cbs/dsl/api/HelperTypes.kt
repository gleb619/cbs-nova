package cbs.dsl.api

interface HelperInput

interface HelperOutput

interface HelperFunction<I : HelperInput, O : HelperOutput> {
  fun execute(input: I): O
}
