package cbs.nova.model;

import java.time.Instant;

/**
 * Standard API error response payload.
 *
 * @param timestamp the moment the error occurred (ISO-8601)
 * @param status the HTTP status code
 * @param error the error type summary
 * @param message the human-readable error message
 * @param path the request path that triggered the error (may be null)
 */
public record ErrorResponse(
    Instant timestamp, int status, String error, String message, String path) {}
