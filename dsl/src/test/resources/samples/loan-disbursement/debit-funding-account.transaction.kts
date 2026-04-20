transaction("DEBIT_FUNDING_ACCOUNT") {
    execute { ctx ->
        ctx["debitTxId"] = "TX-001"
    }
}
