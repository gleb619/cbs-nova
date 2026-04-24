package cbs.dsl.api

sealed class TriggerDefinition {
  data class CronTrigger(val expression: String) : TriggerDefinition()

  data class OnceTrigger(val at: java.time.Instant) : TriggerDefinition()

  data class EveryTrigger(val days: Int = 0, val hours: Int = 0, val minutes: Int = 0) :
      TriggerDefinition()

  data class SignalTrigger(val signal: SignalTypes.Signal) : TriggerDefinition()
}
