package cbs.nova.starter.helper.model;

import java.util.Map;

/**
 * Output for the built-in {@code mergeRecords} helper.
 *
 * <p>
 * {@code result} is a {@code Map<String, Object>} containing the merged record.
 */
public record MergeRecordsOut(Map<String, Object> result) {
}
