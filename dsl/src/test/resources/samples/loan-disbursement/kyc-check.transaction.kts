transaction("KYC_CHECK") {
    execute { ctx ->
        ctx["kycVerified"] = true
    }
}
