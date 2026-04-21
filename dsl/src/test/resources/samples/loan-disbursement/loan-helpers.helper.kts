helpers {
    helper("LOAN_CONDITIONS_BY_ID") {
        name("TestLoanConditionsById")
        execute { ctx -> mapOf("loanId" to ctx.params["loanId"], "currency" to "USD") }
    }
}
