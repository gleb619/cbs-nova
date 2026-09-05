package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code compression} helper.
 *
 * <p>
 * {@code mode} must be one of {@code "gzip"}, {@code "gunzip"}, {@code "deflate"},
 * {@code "inflate"} (case-insensitive). {@code input} is the UTF-8 string to compress or the
 * base64-encoded compressed payload to decompress. {@code level} is the deflate compression level
 * (0-9) used by {@code "gzip"} and {@code "deflate"}; it defaults to {@code -1}
 * ({@link java.util.zip.Deflater#DEFAULT_COMPRESSION}) when null.
 */
public record CompressionIn(String mode, String input, Integer level) {
}
