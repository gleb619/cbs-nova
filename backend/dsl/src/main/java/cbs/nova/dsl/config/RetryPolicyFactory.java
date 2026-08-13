package cbs.nova.dsl.config;

import cbs.nova.dsl.model.RetryPolicy;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.time.Duration;

@RequiredArgsConstructor
public final class RetryPolicyFactory {

  public @NonNull RetryPolicy defaults() {
    return new RetryPolicy(3, Duration.ofSeconds(1), 2.0);
  }
}
