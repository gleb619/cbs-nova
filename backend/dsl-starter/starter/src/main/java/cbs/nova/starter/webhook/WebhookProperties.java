package cbs.nova.starter.webhook;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("cbs.nova.dsl.webhooks")
public class WebhookProperties {

  public static final int DEFAULT_MAX_RETRIES = 3;
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
  public static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofSeconds(2);

  private boolean enabled = false;

  private List<WebhookSubscription> subscriptions = new ArrayList<>();

  private int maxRetries = DEFAULT_MAX_RETRIES;

  private Duration timeout = DEFAULT_TIMEOUT;

  private Duration retryBackoff = DEFAULT_RETRY_BACKOFF;

  private boolean allowPlainHttp = false;
}
