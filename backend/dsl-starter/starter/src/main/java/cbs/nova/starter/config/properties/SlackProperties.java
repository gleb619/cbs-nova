package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration knobs for the optional Slack incoming-webhook notification sink.
 *
 * <p>
 * Example YAML:
 *
 * <pre>
 * cbs.dsl.notifications.slack:
 *   webhook-url: https://hooks.slack.com/services/...
 * </pre>
 */
@ConfigurationProperties(prefix = "cbs.dsl.notifications.slack")
public record SlackProperties(String webhookUrl) {
}
