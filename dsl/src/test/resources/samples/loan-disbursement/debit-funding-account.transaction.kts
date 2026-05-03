transaction("DEBIT_FUNDING_ACCOUNT") {
    name("TestDebitFundingAccount")
    execute { ctx ->
        ctx["debitTxId"] = "TX-001"
    }
}
