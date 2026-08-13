package cbs.nova.starter.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "temporal")
@Validated
public record TemporalTestProperties(@NotBlank String target) {
}
