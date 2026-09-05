package cbs.nova.starter.helper.model;

import java.util.Map;

/**
 * Output for the built-in {@code httpAuth} helper.
 *
 * <p>
 * {@code headers} is the resulting authentication header map — typically a single-entry map keyed
 * by the relevant header name (e.g. {@code "Authorization"} or {@code "X-Api-Key"}).
 */
public record HttpAuthOut(Map<String, String> headers) {
}
