package cbs.nova.starter.helper.model;

import java.util.Map;

/**
 * Output for the built-in {@code parseYaml} helper.
 *
 * <p>
 * The nested structure mirrors the YAML document: maps become {@code Map<String, Object>}, lists
 * become {@code List<Object>}, scalars become {@code String}, {@code Integer}, {@code Long},
 * {@code Double}, {@code Boolean}, or {@code null}. Use a follow-up {@code extractYaml} helper to
 * narrow into the tree (deferred).
 */
public record ParseYamlOut(Map<String, Object> data) {
}
