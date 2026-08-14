package cbs.nova.starter.models;

import cbs.nova.dsl.history.DslRun;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single execution error, matching the frontend {@code ExecutionDetail.errors[]} shape.
 *
 * <p>
 * {@link DslRun} only stores a flat error message today, so {@code code} and
 * {@code stackTrace} are always {@code null} (omitted from the payload) until the backend captures
 * them separately.
 */
public record ErrorEntry(
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code,
        @JsonInclude(JsonInclude.Include.NON_NULL) String stackTrace) {

}
