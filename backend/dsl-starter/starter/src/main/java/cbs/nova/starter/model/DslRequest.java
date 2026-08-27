package cbs.nova.starter.model;

import java.util.Map;

/**
 * Inbound payload accepted by the DSL runtime endpoints
 * ({@code /preview/{name}}, {@code /run/{name}}, {@code /explain/{name}}).
 *
 * @param body the DSL process input — opaque to the framework, marshalled straight into the
 *             execution {@link cbs.nova.dsl.Context}.
 * @param metadata optional side-band metadata forwarded to the context for logging/diagnostics.
 */
public record DslRequest(Object body, Map<String, Object> metadata) {

}