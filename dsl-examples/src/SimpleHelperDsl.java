import cbs.dsl.api.DslObject;
import cbs.dsl.builder.Dsl;

import java.util.List;

// Minimal single-helper DSL with no main method.

List<DslObject> define() {
    return Dsl.helpers()
        .helper("SIMPLE_GREETING", h -> h
            .parameters(reg -> reg.string("name"))
            .execute(ctx -> ctx.put("greeting", "Hello, " + ctx.get("name")))
        )
        .build();
}
