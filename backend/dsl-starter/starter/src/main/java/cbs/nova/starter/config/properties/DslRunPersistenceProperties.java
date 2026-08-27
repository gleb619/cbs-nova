package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the {@code dsl_runs} persistence layer.
 */
@ConfigurationProperties(prefix = "cbs.nova.persistence.run")
public record DslRunPersistenceProperties(
        String schema,
        String tableName,
        boolean encryptionEnabled,
        String encryptionKey) {
}
