package cbs.nova.starter.helper.model;

import java.util.List;
import java.util.Map;

/**
 * Output for the built-in {@code groupRecords} helper.
 *
 * <p>
 * {@code groups} maps each resolved composite key to the stable list of records that share it.
 */
public record GroupRecordsOut(Map<Object, List<Map<String, Object>>> groups) {
}
