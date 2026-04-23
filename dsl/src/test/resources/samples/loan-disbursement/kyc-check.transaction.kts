transaction("KYC_CHECK") {
  name("TestKycCheck")
  execute { ctx ->
    ctx["kycVerified"] = true
    ctx["_kycImpl"] = "TestTransaction"
  }
}
