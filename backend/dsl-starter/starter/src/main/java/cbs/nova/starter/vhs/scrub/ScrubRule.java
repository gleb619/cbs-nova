package cbs.nova.starter.vhs.scrub;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A single record-time scrubbing rule.
 *
 * <p>
 * A rule selects one JSON path inside a tape event's {@code input}/{@code output}/{@code metadata}
 * tree and applies an {@link Action} to it. Paths use dot notation for map/record fields and
 * {@code [*]} for array elements (for example {@code $.token}, {@code $.accounts[*].iban}).
 *
 * @param path
 *          the JSON path to scrub, e.g. {@code $.cardNumber}
 * @param action
 *          what to do with the matched field
 * @param pieceId
 *          optional reference to a {@code piece.id} from {@code docs/vhs-manifest.md} (T547); used
 *          as a doc-only tag when the manifest loader is not yet wired in (T556 matches by path
 *          alone)
 */
public record ScrubRule(
        @NonNull String path,
        @NonNull Action action,
        @Nullable String pieceId) {

  public ScrubRule {
    action = action == null ? Action.mask : action;
  }

  /** What to do with a matched field. */
  public enum Action {

    /** Delete the field entirely so it never appears in the persisted tape. */
    remove,

    /** Replace the value with the configured {@code maskValue}. */
    mask,

    /** Replace the value with a deterministic fake derived from the original + seed. */
    fake
  }
}
