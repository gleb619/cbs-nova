package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code jsonPatch} helper.
 *
 * <p>
 * {@code mode} selects the operation and must be {@code "apply"} or {@code "diff"}
 * (case-insensitive):
 * <ul>
 * <li>{@code "apply"}: merges {@code patch} into {@code source} per RFC 7396 and returns the
 * resulting object. {@code target} is ignored.</li>
 * <li>{@code "diff"}: produces an RFC 7396 merge patch that, when applied to {@code source}, yields
 * {@code target}. {@code patch} is ignored.</li>
 * </ul>
 * {@code source}, and either {@code patch} (for apply) or {@code target} (for diff), must be valid
 * JSON object strings.
 */
public record JsonPatchIn(String source, String patch, String target, String mode) {
}
