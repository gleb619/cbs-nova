package cbs.nova.starter.helper.model;

import java.time.Instant;

/**
 * Output for the built-in {@code s3Storage} helper.
 *
 * <p>
 * {@code op}, {@code bucket} and {@code key} echo the executed operation. For {@code put} and
 * {@code delete}, {@code bodyBase64} and {@code contentType} are {@code null}. For {@code get},
 * {@code bodyBase64} carries the object bytes and {@code contentType} the stored MIME type.
 * {@code timestamp} records when the call completed.
 */
public record S3StorageOut(
        String op,
        String bucket,
        String key,
        String bodyBase64,
        String contentType,
        Instant timestamp) {
}
