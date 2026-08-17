package cbs.nova.starter.models;

import cbs.nova.dsl.history.DslRun;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ErrorEntry(
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code,
        @JsonInclude(JsonInclude.Include.NON_NULL) String stackTrace) {

}
