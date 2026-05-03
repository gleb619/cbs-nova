condition("BORROWER_ACCOUNT_READY") {
    predicate { ctx -> ctx.enrichment["accountCode"] != null }
}
