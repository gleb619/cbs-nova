package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code jwt} helper.
 *
 * <p>
 * The shape of {@code result} depends on {@code mode}:
 * <ul>
 * <li>{@code parse}: a {@link java.util.Map} with keys {@code "header"} (Map&lt;String,Object&gt;),
 * {@code "payload"} (Map&lt;String,Object&gt;), and {@code "signature"} (the raw base64url
 * signature segment as a {@link String}, NOT decoded or verified).</li>
 * <li>{@code verify}: a {@link java.util.Map} with keys {@code "header"} and {@code "payload"}
 * (both Map&lt;String,Object&gt;), returned only after signature and time-based claim checks
 * succeed.</li>
 * <li>{@code sign}: the compact JWS string {@code "header.payload.signature"} as a
 * {@link String}.</li>
 * <li>{@code claim}: the value of the requested claim as an {@link Object} (type depends on the
 * claim: typically {@link Long}, {@link String}, {@link Boolean}, {@link java.util.List}, or
 * {@link java.util.Map}).</li>
 * </ul>
 */
public record JwtOut(Object result) {
}
