import java.util.List;
import java.util.Map;

/**
 * I/O records for {@code RecordReconcileDsl}: candidate-deduplication + field-level
 * reconciliation via {@code similarity} (jaroWinkler / ratio / levenshtein) and
 * {@code jsonDiff}.
 *
 * <p>The dedupe pass scores {@link RecordReconcileIn#candidate()} against each entry
 * in {@link RecordReconcileIn#candidates()} under the requested {@code mode} and picks
 * the highest-scoring match above {@code matchThreshold}. The reconcile pass then diffs
 * the matched record's JSON payload against {@link RecordReconcileIn#candidateJson()}
 * (the new/updated view) to surface field-level changes.
 */
public class RecordReconcileModels {

  /**
   * Inbound payload for {@code RecordReconcile}.
   *
   * @param candidateName  display name of the inbound record (used for similarity scoring)
   * @param candidates     existing records to score against; each map is treated as a
   *                       JSON object whose {@code name} field is compared to
   *                       {@code candidateName}
   * @param candidateJson  JSON-encoded view of the inbound record's full payload
   *                       (used as the right-hand side of the {@code jsonDiff} call)
   * @param matchThreshold similarity score in [0.0, 1.0] above which a candidate is
   *                       accepted as the reconciled match
   * @param mode           similarity mode — {@code "jaroWinkler"} (default-ish for
   *                       name matching), `levenshtein`, or `ratio`
   * @param candidateField map key inside each candidate map that holds the comparable
   *                       name (defaults to {@code "name"} when null)
   */
  public record RecordReconcileIn(
      String candidateName,
      List<Map<String, Object>> candidates,
      String candidateJson,
      double matchThreshold,
      String mode,
      String candidateField) {
  }

  /**
   * Result of the dedupe + reconcile pipeline.
   *
   * <p>{@code matched} is true when the best score is &ge; {@code matchThreshold}.
   * {@code matchedIndex} is the index of that candidate (or {@code -1} when no
   * candidate clears the threshold). {@code identical} is the {@code jsonDiff}
   * flag for the matched candidate vs the candidate JSON; {@code changeCount} is
   * the number of per-path entries returned by the diff. {@code firstChangePath}
   * / {@code firstChangeOp} capture the leading diff entry so a test can pin them
   * without asserting the entire change list.
   */
  public record RecordReconcileOut(
      String mode,
      String candidateName,
      String bestCandidateName,
      double bestScore,
      double matchThreshold,
      boolean matched,
      int matchedIndex,
      boolean identical,
      int changeCount,
      String firstChangePath,
      String firstChangeOp) {
  }
}