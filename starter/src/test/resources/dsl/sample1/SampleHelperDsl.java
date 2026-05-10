import cbs.dsl.api.DslObject;
import cbs.dsl.builder.Dsl;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import java.util.Map;

List<DslObject> define() {
    return Dsl.helpers()
        .helper("SAMPLE_HELPER_DSL", h -> h
            .parameters(reg -> reg
                .string("name"))

            .context(ctx ->
                ctx.put("extraName",
                    ctx.helper("SAMPLE_HELPER",
                        Map.of("name", ctx.get("name"))
                    )
                )
            )
        )
        .build();
}