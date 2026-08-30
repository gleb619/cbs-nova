package cbs.nova.starter.model;

/**
 * Structured validation problem exposed by {@code ProblemsPanel} on the frontend.
 *
 * @param field
 *          JSONPath-like pointer to the offending value ({@code $}, {@code $.field},
 *          {@code $.items[0].field}).
 * @param message
 *          Human-readable description of the problem.
 * @param severity
 *          {@code error} or {@code warning}; validation failures are always {@code error}.
 */
public record ValidationError(String field, String message, String severity) {
}
