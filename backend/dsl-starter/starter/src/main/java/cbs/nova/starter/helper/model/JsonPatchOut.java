package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code jsonPatch} helper.
 *
 * <p>
 * {@code result} is a compact (no-whitespace) JSON string: the merged object for {@code "apply"}
 * mode, or the merge patch document for {@code "diff"} mode.
 */
public record JsonPatchOut(String result) {
}
