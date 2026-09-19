package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the optional domain-event message-queue publisher.
 */
@ConfigurationProperties(prefix = "cbs.nova.events.mq")
public record MqEventProperties(
        boolean enabled,
        String exchange,
        String topic,
        String routingKey,
        int retryBatchSize,
        long retryIntervalSeconds) {

  public MqEventProperties {
    if (retryBatchSize <= 0) {
      retryBatchSize = 100;
    }
    if (retryIntervalSeconds <= 0) {
      retryIntervalSeconds = 30;
    }
  }

  public String effectiveRoutingKey(String eventType) {
    if (routingKey != null && !routingKey.isBlank()) {
      return routingKey;
    }
    if (topic != null && !topic.isBlank()) {
      return topic + "." + eventType;
    }
    return eventType;
  }
}
