package cbs.dsl.runtime

import cbs.dsl.api.Action
import cbs.dsl.api.EventDefinition
import cbs.dsl.api.TransitionRule
import cbs.dsl.api.context.TransactionsScope

/** Intermediate builder: holds from+to+action, waiting for event closure */
class TransitionActionBuilder(
  val from: String,
  val to: String,
  val on: Action,
)

/** Final builder: holds complete transition, supports onFault */
data class TransitionBuilder(
  val from: String,
  val to: String,
  val on: Action,
  val event: EventDefinition,
  val onFaultState: String = "FAULTED",
  val onFaultBlock: (suspend TransactionsScope.() -> Unit)? = null,
)

/** DSL scope for the transitions { } block */
class TransitionScope {
  internal val rules = mutableListOf<TransitionRule>()

  /** "DRAFT" to "ENTERED" on Action.SUBMIT → TransitionActionBuilder */
  infix fun Pair<String, String>.on(action: Action): TransitionActionBuilder =
    TransitionActionBuilder(first, second, action)

  /** TransitionActionBuilder { event block } → TransitionBuilder (registers rule) */
  operator fun TransitionActionBuilder.invoke(
    eventBlock: EventBuilder.() -> Unit
  ): TransitionBuilder {
    val ev = EventBuilder("${from}_${to}_${on.name}").apply(eventBlock)
    val tb = TransitionBuilder(from, to, on, ev)
    rules += tb.build()
    return tb
  }

  /** TransitionBuilder onFault { fault block } → amends last rule */
  infix fun TransitionBuilder.onFault(
    block: suspend TransactionsScope.() -> Unit
  ): TransitionBuilder {
    // Replace last rule with updated onFaultBlock
    rules[rules.lastIndex] = build().copy(onFaultBlock = block)
    return copy(onFaultBlock = block)
  }

  private fun TransitionBuilder.build(): TransitionRule =
    TransitionRule(from, to, on, event, onFaultState, onFaultBlock)
}
