package cbs.nova.starter.helper.model;

import java.util.List;

/**
 * Output for the built-in {@code interpolate} helper.
 *
 * @param result
 *          the rendered template string.
 * @param resolvedKeys
 *          the distinct keys actually substituted, in first-seen order. Useful as an observability
 *          / test hook (e.g. "did we touch the secrets key?").
 */
public record InterpolateOut(String result, List<String> resolvedKeys) {
}
