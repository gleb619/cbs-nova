// #import code:cbs.dsl.impl.TestHelper

helpers {
  helper("TestHelper") { ctx ->
    name("OverloadExampleHelper")

    parameters {
      required("value")
      required("param")
    }

    execute { ctx ->
      ctx.runHelper(mapOf(
        "value" to ctx["value"],
        "param" to "${ctx["value"]}-param"
      ))
    }
  }

  helper("TestHelper") { ctx ->
    name("OverrideExampleHelper")

    context { ctx ->
      ctx["value"] = "${ctx["value"]}-override"
    }

  }
}
