package cbs.nova.starter.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class WebhookPropertiesTest {

  @Test
  void defaultsBindCorrectly() {
    Map<String, String> map = Map.of(
            "cbs.nova.dsl.webhooks.enabled", "true");

    WebhookProperties properties = bind(map);

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getSubscriptions()).isEmpty();
    assertThat(properties.getMaxRetries()).isEqualTo(3);
    assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(properties.getRetryBackoff()).isEqualTo(Duration.ofSeconds(2));
    assertThat(properties.isAllowPlainHttp()).isFalse();
  }

  @Test
  void subscriptionsAndScalarsBindCorrectly() {
    Map<String, String> map = Map.ofEntries(
            Map.entry("cbs.nova.dsl.webhooks.enabled", "true"),
            Map.entry("cbs.nova.dsl.webhooks.max-retries", "5"),
            Map.entry("cbs.nova.dsl.webhooks.timeout", "PT10S"),
            Map.entry("cbs.nova.dsl.webhooks.retry-backoff", "PT3S"),
            Map.entry("cbs.nova.dsl.webhooks.allow-plain-http", "true"),
            Map.entry("cbs.nova.dsl.webhooks.subscriptions[0].definition-pattern", "orders-*"),
            Map.entry("cbs.nova.dsl.webhooks.subscriptions[0].url", "https://example.com/hook"),
            Map.entry("cbs.nova.dsl.webhooks.subscriptions[0].secret", "secret"),
            Map.entry("cbs.nova.dsl.webhooks.subscriptions[0].statuses[0]", "FAILED"),
            Map.entry("cbs.nova.dsl.webhooks.subscriptions[1].definition-pattern", "*"),
            Map.entry("cbs.nova.dsl.webhooks.subscriptions[1].url", "https://example.com/all"));

    WebhookProperties properties = bind(map);

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getMaxRetries()).isEqualTo(5);
    assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(properties.getRetryBackoff()).isEqualTo(Duration.ofSeconds(3));
    assertThat(properties.isAllowPlainHttp()).isTrue();
    assertThat(properties.getSubscriptions()).hasSize(2);

    WebhookSubscription first = properties.getSubscriptions().get(0);
    assertThat(first.definitionPattern()).isEqualTo("orders-*");
    assertThat(first.url()).isEqualTo("https://example.com/hook");
    assertThat(first.secret()).isEqualTo("secret");
    assertThat(first.statuses()).containsExactly("FAILED");

    WebhookSubscription second = properties.getSubscriptions().get(1);
    assertThat(second.definitionPattern()).isEqualTo("*");
    assertThat(second.url()).isEqualTo("https://example.com/all");
    assertThat(second.secret()).isNull();
    assertThat(second.statuses()).isNull();
  }

  @Test
  void disabledByDefault() {
    WebhookProperties properties = bind(Map.of());

    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.getSubscriptions()).isEmpty();
  }

  private WebhookProperties bind(Map<String, String> map) {
    return new Binder(new MapConfigurationPropertySource(map))
            .bind("cbs.nova.dsl.webhooks", WebhookProperties.class)
            .orElse(new WebhookProperties());
  }
}
