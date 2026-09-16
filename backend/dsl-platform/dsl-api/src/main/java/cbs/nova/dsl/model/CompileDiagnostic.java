package cbs.nova.dsl.model;

import com.fasterxml.jackson.annotation.JsonInclude;

public record CompileDiagnostic(
        String file,
        Long line,
        Long column,
        String message,
        String severity,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code) {

}
