package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entry from a persisted run trace. Produced by {@link ExecutionDto#fromDetail} from the
 * {@code context_json} blob the runner writes on completion.
 *
 * <p>
 * The shape mirrors the FE {@code TraceStep} contract (id, stepType, name, isCompensation). Status
 * / duration / timestamps are intentionally omitted on the wire — the persisted trace is a flat
 * ordered log without per-step timing, so adding those fields would imply a richer storage format
 * than the runner currently emits.
 *
 * @param id
 *          stable string identifier — the 0-based index of the entry in the captured trace list
 * @param stepType
 *          one of {@code Process}, {@code Transaction}, {@code Helper} — derived from the trace
 *          entry prefix by {@link ExecutionDto#toTraceSteps}
 * @param name
 *          human-readable label for the step
 * @param isCompensation
 *          true for every entry at or after the first {@code "compensation log:"} line in the
 *          captured trace; the flat list has no better signal for phase boundary
 */
public record TraceStepDto(
        String id,
        String stepType,
        String name,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean isCompensation) {

}
