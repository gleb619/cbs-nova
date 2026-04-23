// #import loan-disbursement.* as disb

event("LOAN_DISBURSEMENT") {
  parameters {
    required("customerId")
    required("loanId")
    required("amount")
    optional("accountNumber")
  }
  context { ctx ->
    ctx["customerCode"] =
        ctx.helper("FIND_CUSTOMER_CODE_BY_ID", mapOf("id" to ctx.eventParameters["customerId"]!!))
    ctx["loanConditions"] =
        ctx.helper("LOAN_CONDITIONS_BY_ID", mapOf("loanId" to ctx.eventParameters["loanId"]!!))
  }
  transactions {
    val disb = imports["disb"]!!
    val kyc = step(disb["KYC_CHECK"] as TransactionDefinition)
    val scoring = step(disb["CREDIT_SCORING"] as TransactionDefinition)
    val debit = step(disb["DEBIT_FUNDING_ACCOUNT"] as TransactionDefinition)
    val credit = step(disb["CREDIT_BORROWER_ACCOUNT"] as TransactionDefinition)
    await(kyc, scoring, debit, credit)
  }
  finish { ctx, ex -> ctx["disbursed"] = ex == null }
}
