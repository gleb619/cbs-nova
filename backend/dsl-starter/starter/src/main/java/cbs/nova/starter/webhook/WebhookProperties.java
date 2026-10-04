package cbs.nova.starter.webhook;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.security.WebhookUrlGuard;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("cbs.nova.dsl.webhooks")
public class WebhookProperties {

  private boolean enabled = false;

  private List<WebhookSubscription> subscriptions = new ArrayList<>();

  private int maxRetries = StarterConstants.DEFAULT_MAX_RETRIES;

  private Duration timeout = StarterConstants.DEFAULT_TIMEOUT;

  private Duration retryBackoff = StarterConstants.DEFAULT_RETRY_BACKOFF;

  private boolean allowPlainHttp = false;

  private List<String> allowedSchemes = new ArrayList<>(List.of("https", "http"));

  private List<String> allowedHosts = new ArrayList<>();

  private boolean blockPrivateAddresses = false;

  /**
   * Validates every configured subscription URL after Spring binds the properties. This is the
   * static-subscription equivalent of "create/update rejects unsafe URLs" — an illegal URL fails
   * bean creation with {@link IllegalArgumentException} so it cannot be persisted or delivered.
   */
  @PostConstruct
  public void validate() {
    if (subscriptions == null) {
      return;
    }
    for (WebhookSubscription subscription : subscriptions) {
      if (subscription != null && subscription.url() != null) {
        WebhookUrlGuard.validateSubscription(subscription.url(), this);
      }
    }
  }
}
