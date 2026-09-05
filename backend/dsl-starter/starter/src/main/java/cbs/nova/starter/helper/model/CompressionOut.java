package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code compression} helper.
 *
 * <p>
 * {@code result} is the base64-encoded compressed payload (for {@code "gzip"} and
 * {@code "deflate"}) or the UTF-8 string recovered after decompression (for {@code "gunzip"} and
 * {@code "inflate"}).
 */
public record CompressionOut(String result) {
}
