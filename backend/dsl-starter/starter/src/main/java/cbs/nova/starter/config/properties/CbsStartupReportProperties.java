package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "cbs.nova.startup-report")
public record CbsStartupReportProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") int topSteps,
        @DefaultValue("50") long slowThresholdMillis) {
}
