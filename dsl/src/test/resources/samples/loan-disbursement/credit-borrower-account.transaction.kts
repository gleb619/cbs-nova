transaction("CREDIT_BORROWER_ACCOUNT") {
    name("TestCreditBorrowerAccount")
    execute { ctx ->
        ctx["creditTxId"] = "TX-002"
    }
}
