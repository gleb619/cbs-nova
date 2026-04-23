// #import loan-disbursement.* as disb

workflow("LOAN_CONTRACT") {
  states("DRAFT", "ENTERED", "ACTIVE", "CANCELLED", "CLOSED", "FAULTED")
  initial("ENTERED")
  terminalStates("CLOSED", "CANCELLED")
  transitions {
    (("DRAFT" to "ENTERED") on Action.SUBMIT) { context { ctx -> } }
    (("ENTERED" to "ACTIVE") on Action.APPROVE) { context { ctx -> } }
    (("ENTERED" to "CANCELLED") on Action.CANCEL) { context { ctx -> } }
  }
}
