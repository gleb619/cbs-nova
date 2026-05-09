import cbs.dsl.builder.Dsl;
import cbs.dsl.api.Action;

Dsl.workflow("LOAN_DISBURSEMENT_WF")
    .states("START", "REVIEW", "APPROVED", "REJECTED", "DONE")
    .initial("START")
    .terminal("DONE", "REJECTED")
    .transition("START", "REVIEW", Action.SUBMIT, "LOAN_DISBURSEMENT")
    .transition("REVIEW", "APPROVED", Action.APPROVE, "LOAN_DISBURSEMENT")
    .transition("REVIEW", "REJECTED", Action.REJECT, "LOAN_DISBURSEMENT")
    .transition("APPROVED", "DONE", Action.CLOSE, "LOAN_DISBURSEMENT")
    .build();
