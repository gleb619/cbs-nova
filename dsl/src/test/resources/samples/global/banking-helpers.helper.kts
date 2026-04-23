helpers {
  helper("FIND_BANK_ACCOUNT") {
    name("TestFindBankAccount")
    execute { ctx -> mapOf("iban" to ctx.params["iban"]) }
  }
  helper("FIND_CUSTOMER_CODE_BY_ID") {
    name("TestFindCustomerCodeById")
    execute { ctx -> "CUST-${ctx.params["id"]}-via-TestHelper" }
  }
}
