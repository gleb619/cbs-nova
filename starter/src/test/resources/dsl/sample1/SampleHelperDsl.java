import cbs.dsl.api.DslObject;
import cbs.dsl.builder.Dsl;
import java.util.Map;

List<DslObject> define() {
    return Dsl.helpers()
        /* We register a new helper called `SAMPLE_HELPER_VIA_DSL` */
        .helper("SAMPLE_HELPER_VIA_DSL", h -> h
            /* We describe parameters that are needed to run the helper */
            .parameters(reg -> reg
                .string("name"))

            // Context enrichment: calculated values when parameters alone are not enough.
            // name is in parameters, but we need extraName from a helper call.

            .context(ctx ->
                ctx.put("extraName",
                    ctx.runHelper("SAMPLE_HELPER",
                        Map.of("name", ctx.get("name"))
                    )
                )
            )

            /* On execute we just populate the output map with some data from enriched context */
            .execute(ctx -> ctx.put("someValue", ctx.get("extraName")))
        )
        .build();
}
