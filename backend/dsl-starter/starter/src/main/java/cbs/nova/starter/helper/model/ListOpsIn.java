package cbs.nova.starter.helper.model;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code listOps} helper.
 *
 * <p>
 * Only the fields required by the selected {@code mode} are used; the remaining fields may be
 * {@code null}:
 * <ul>
 * <li>{@code "pluck"}, {@code "groupBy"}, {@code "countBy"}, {@code "sumBy"}, {@code "minBy"},
 * {@code "maxBy"} require {@code records} and {@code field}.</li>
 * <li>{@code "flatten"} requires {@code nested}; {@code depth} defaults to {@code 1} when null, and
 * {@code -1} means fully flatten.</li>
 * <li>{@code "distinct"} requires {@code list}.</li>
 * </ul>
 * {@code mode} is matched case-insensitively.
 */
public record ListOpsIn(
        String mode,
        @Nullable List<Map<String, Object>> records,
        @Nullable List<Object> list,
        @Nullable List<Object> nested,
        @Nullable String field,
        @Nullable Integer depth) {
}
