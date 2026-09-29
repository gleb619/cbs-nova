package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code similarity} helper.
 *
 * <p>
 * {@code mode} selects the similarity algorithm and must be one of {@code "levenshtein"},
 * {@code "jaroWinkler"}, or {@code "ratio"} (case-insensitive). Both {@code a} and {@code b} are
 * required; either being {@code null} fails the call. {@code caseSensitive} defaults to
 * {@code false} when {@code null}; when false, both inputs are lower-cased via
 * {@link java.util.Locale#ROOT} before scoring.
 */
public record SimilarityIn(
        String mode,
        String a,
        String b,
        Boolean caseSensitive) {
}
