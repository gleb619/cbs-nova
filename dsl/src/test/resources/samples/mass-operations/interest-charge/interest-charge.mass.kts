massOperation("INTEREST_CHARGE") {
  category("CREDITS")
  trigger(TriggerDefinition.CronTrigger("0 1 * * *"))
  source { ctx -> emptyList() }
  item { ctx ->
    // process interest charge for ctx.eventParameters["accountId"]
  }
}
