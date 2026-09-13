package cbs.nova.starter.helper.model;

import java.util.Map;

/**
 * Output for the built-in {@code pick} helper: the projected map.
 */
public record PickOut(Map<String, Object> result) {
}
