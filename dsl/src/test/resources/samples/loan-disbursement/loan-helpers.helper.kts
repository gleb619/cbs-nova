helpers {
    helper("LOAN_CONDITIONS_BY_ID") {
        execute { ctx -> mapOf("loanId" to ctx.params["loanId"], "currency" to "USD") }
    }
}
