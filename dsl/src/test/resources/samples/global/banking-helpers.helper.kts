helpers {
    helper("FIND_BANK_ACCOUNT") {
        execute { ctx -> mapOf("iban" to ctx.params["iban"]) }
    }
    helper("FIND_CUSTOMER_CODE_BY_ID") {
        execute { ctx -> "CUST-${ctx.params["id"]}" }
    }
}
