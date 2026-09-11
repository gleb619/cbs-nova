package cbs.nova.starter.webhook;

import cbs.nova.starter.core.StarterConstants;
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
}
