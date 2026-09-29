package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code similarity} helper.
 *
 * <p>
 * {@code score} is the normalized similarity in {@code [0.0, 1.0]}, where {@code 1.0} means equal
 * strings under the selected algorithm and {@code caseSensitive} flag.
 */
public record SimilarityOut(double score) {
}
