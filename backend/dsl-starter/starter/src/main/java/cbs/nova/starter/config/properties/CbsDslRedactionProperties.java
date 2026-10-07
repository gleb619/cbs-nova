package cbs.nova.starter.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for run-data redaction at egress ({@code cbs.dsl.redaction.*}).
 *
 * <p>
 * Redaction is applied only when data leaves the system (API DTOs, CSV export, VHS tape download,
 * domain-event read payloads, audit details). It never changes what is persisted or what internal
 * replay/re-run/reconciliation paths read.
 */
@ConfigurationProperties(prefix = "cbs.dsl.redaction")
public record CbsDslRedactionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue List<String> keys,
        @DefaultValue("***") String replacement,
        @DefaultValue List<String> bypassRoles,
        @DefaultValue("10000000") int maxInputLength) {

  public CbsDslRedactionProperties {
    keys = keys == null || keys.isEmpty()
            ? List.of(
                    "password|passwd|secret|token|authorization|api[-_]?key|private[-_]?key|credential")
            : List.copyOf(keys);
    replacement = replacement == null || replacement.isBlank() ? "***" : replacement;
    bypassRoles = bypassRoles == null || bypassRoles.isEmpty()
            ? List.of("ADMIN", "ROLE_ADMIN")
            : List.copyOf(bypassRoles);
    maxInputLength = maxInputLength <= 0 ? 10_000_000 : maxInputLength;
  }
}
