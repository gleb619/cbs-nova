import cbs.dsl.api.DslObject;
import cbs.dsl.builder.Dsl;
import cbs.dsl.api.Action;
import java.util.List;

List<DslObject> define() {
    return List.of(Dsl.workflow("DSL_TEST_WF")
        .states("START", "DONE")
        .initial("START")
        .terminal("DONE")
        .transition("START", "DONE", Action.SUBMIT, "SAMPLE_EVENT_VIA_DSL")
        .build());
}
