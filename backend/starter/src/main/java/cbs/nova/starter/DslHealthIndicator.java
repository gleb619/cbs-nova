package cbs.nova.starter;

import cbs.nova.dsl.GlobalManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(HealthIndicator.class)
public class DslHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    GlobalManager gm = GlobalManager.getInstance();
    return Health.up()
            .withDetail("processes", gm.processNames().size())
            .withDetail("transactions", gm.transactionNames().size())
            .withDetail("helpers", gm.helperNames().size())
            .build();
  }
}
