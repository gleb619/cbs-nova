package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code s3Storage} helper.
 *
 * <p>
 * {@code op} selects the operation: {@code put}, {@code get} or {@code delete}. {@code bucket} and
 * {@code key} are always required. For {@code put}, {@code body} holds the object content (plain
 * text after DSL interpolation) and {@code contentType} its MIME type (optional, defaults to
 * {@code application/octet-stream}). For {@code get}, {@code body} must be {@code null}; the
 * returned content is exposed base64-encoded.
 */
public record S3StorageIn(String op, String bucket, String key, String body, String contentType) {
}
