package cbs.nova.starter.helper.model;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code otel} helper.
 *
 * <p>
 * The {@code mode} discriminator selects one of seven operations:
 * <ul>
 * <li>{@code "span"} — requires {@code name}; optional {@code attributes}.</li>
 * <li>{@code "endSpan"} — requires {@code spanId}; optional {@code statusCode} (default {@code OK})
 * and {@code errorMessage}.</li>
 * <li>{@code "addEvent"} — requires {@code spanId} and {@code eventName}; optional
 * {@code attributes}.</li>
 * <li>{@code "setBaggage"} — requires {@code baggageKey} and {@code baggageValue}.</li>
 * <li>{@code "getBaggage"} — requires {@code baggageKey}.</li>
 * <li>{@code "injectContext"} — optional {@code headers} carrier (defaults to empty).</li>
 * <li>{@code "extractContext"} — optional {@code headers} carrier; returns {@code ""} when no
 * traceparent is present.</li>
 * </ul>
 *
 * <p>
 * All fields except {@code mode} are nullable; per-mode validation runs inside the helper.
 */
public record OtelIn(
        @Nullable String mode,
        @Nullable String name,
        @Nullable Map<String, String> attributes,
        @Nullable String spanId,
        @Nullable String statusCode,
        @Nullable String errorMessage,
        @Nullable String eventName,
        @Nullable String baggageKey,
        @Nullable String baggageValue,
        @Nullable Map<String, String> headers) {
}
