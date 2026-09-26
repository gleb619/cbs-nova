package cbs.nova.starter.helper.model;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code mergeRecords} helper.
 *
 * <p>
 * {@code records} must contain at least one record. {@code mode} defaults to {@code "shallow"} when
 * null/blank and is matched case-insensitively.
 */
public record MergeRecordsIn(List<Map<String, Object>> records, @Nullable String mode) {
}
