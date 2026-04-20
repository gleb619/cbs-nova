// #import loan-disbursement.* as disb

event("LOAN_DISBURSEMENT") {
    parameters {
        required("customerId")
        required("loanId")
        required("amount")
        optional("accountNumber")
    }
    context { ctx ->
        ctx["customerCode"] = ctx.helper("FIND_CUSTOMER_CODE_BY_ID", mapOf("id" to ctx.eventParameters["customerId"]!!))
        ctx["loanConditions"] = ctx.helper("LOAN_CONDITIONS_BY_ID", mapOf("loanId" to ctx.eventParameters["loanId"]!!))
    }
    transactions {
        // step(disb["KYC_CHECK"])
        // step(disb["CREDIT_BORROWER_ACCOUNT"])
        // step(disb["DEBIT_FUNDING_ACCOUNT"])
    }
    finish { ctx, ex ->
        ctx["disbursed"] = ex == null
    }
}
