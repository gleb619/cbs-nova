package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Opt-in/out switch for server-side input schema validation on run/preview/explain.
 *
 * <p>
 * Default is {@code true}. Record inputs are validated against their generated JSON schema;
 * non-record inputs (e.g. {@code String.class}, {@code Map.class}) are accepted without shape
 * validation because the generator currently only infers schemas for records. Set
 * {@code cbs.runtime.input-validation.enabled=false} to disable globally.
 */
@ConfigurationProperties(prefix = "cbs.runtime.input-validation")
public record InputValidationProperties(@DefaultValue("true") boolean enabled) {
}
