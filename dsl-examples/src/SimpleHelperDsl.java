import cbs.dsl.builder.Dsl;

// Minimal single-helper DSL with no main method.

Object obj = Dsl.helpers()
    .helper("SIMPLE_GREETING", h -> h
        .parameters(reg -> reg.string("name"))
        .execute(ctx -> ctx.put("greeting", "Hello, " + ctx.get("name")))
    )
    .build();
