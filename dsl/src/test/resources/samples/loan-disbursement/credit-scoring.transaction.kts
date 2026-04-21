transaction("CREDIT_SCORING") {
    name("TestCreditScoring")
    preview { ctx ->
        ctx["scoringPreview"] = "pending"
    }
    execute { ctx ->
        ctx["creditScore"] = 720
    }
    rollback { ctx ->
        ctx["creditScore"] = 0
    }
}
