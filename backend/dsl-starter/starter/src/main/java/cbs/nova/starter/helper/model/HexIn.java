package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code hex} helper.
 *
 * <p>
 * {@code mode} must be {@code "encode"} or {@code "decode"} (case-insensitive). Encoding produces
 * lowercase hex of the UTF-8 bytes of {@code input}; decoding parses a hex string and interprets
 * the bytes as UTF-8. Empty input is rejected for both modes.
 */
public record HexIn(String input, String mode) {
}
