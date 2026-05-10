import cbs.dsl.api.Action;
import cbs.dsl.api.DslObject;
import cbs.dsl.builder.Dsl;

import java.util.List;

List<DslObject> define() {
    return List.of(Dsl.workflow("LOAN_DISBURSEMENT_WF")
        .states("START", "REVIEW", "APPROVED", "REJECTED", "DONE")
        .initial("START")
        .terminal("DONE", "REJECTED")
        .transition("START", "REVIEW", Action.SUBMIT, "LOAN_DISBURSEMENT")
        .transition("REVIEW", "APPROVED", Action.APPROVE, "LOAN_DISBURSEMENT")
        .transition("REVIEW", "REJECTED", Action.REJECT, "LOAN_DISBURSEMENT")
        .transition("APPROVED", "DONE", Action.CLOSE, "LOAN_DISBURSEMENT")
        .build());
}
