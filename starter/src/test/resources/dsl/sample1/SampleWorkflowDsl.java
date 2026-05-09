import cbs.dsl.builder.Dsl;
import cbs.dsl.api.Action;

Dsl.workflow("DSL_TEST_WF")
    .states("START", "DONE")
    .initial("START")
    .terminal("DONE")
    .transition("START", "DONE", Action.SUBMIT, "SAMPLE_EVENT_DSL")
    .build();