package cbs.nova.dsl.process;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a preview-mode signal timeline.
 *
 * @param name
 *          signal name as declared via {@code ProcessBuilder.signal(name, type)}
 * @param payloadJson
 *          JSON encoding of the payload; {@code null} means "delivered with no payload"
 * @param deliverAt
 *          zero-based await-point index at which the signal becomes visible; {@code null} or
 *          {@code 0} means "visible from the start of the preview"
 */
@Schema(description = "One entry of a preview-mode signal timeline. "
        + "A seeded signal becomes visible at the zero-based await-point index "
        + "given by deliverAt (absent or 0 = visible from the start of the preview). "
        + "Preview resolves signals from this timeline instead of waiting on wall-clock time.", requiredProperties = {
            "name"})
public record SeededSignal(
        @NonNull @Schema(description = "signal name as declared via ProcessBuilder.signal(name, type)") String name,
        @Nullable @Schema(nullable = true, description = "JSON encoding of the payload; null means delivered with no payload") String payloadJson,
        @Nullable @Schema(nullable = true, description = "zero-based await-point index at which the signal becomes visible; "
                + "null or 0 means visible from the start of the preview") Integer deliverAt) {

  public SeededSignal {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Seeded signal name must not be blank");
    }
    if (deliverAt != null && deliverAt < 0) {
      throw new IllegalArgumentException(
              "Seeded signal deliverAt must not be negative: " + deliverAt);
    }
  }

  public static @NonNull SeededSignal of(@NonNull String name,
          @Nullable String payloadJson) {
    return new SeededSignal(name, payloadJson, null);
  }

  /**
   * Await-point index this signal becomes visible at, defaulting to {@code 0}.
   */
  public int deliverIndex() {
    return deliverAt == null ? 0 : deliverAt;
  }
}
