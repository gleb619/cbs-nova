package cbs.nova.starter.config;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.config.properties.CbsHealthProperties;
import cbs.nova.starter.service.TemporalHealthProbe;
import cbs.nova.starter.service.TemporalHealthProbe.TemporalHealth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(HealthIndicator.class)
public class DslHealthIndicator implements HealthIndicator {

  private final @Nullable ObjectProvider<TemporalHealthProbe> probeProvider;
  private final @Nullable ObjectProvider<CbsHealthProperties> propsProvider;

  public DslHealthIndicator() {
    this(null, null);
  }

  @Autowired
  public DslHealthIndicator(@Nullable ObjectProvider<TemporalHealthProbe> probeProvider,
          @Nullable ObjectProvider<CbsHealthProperties> propsProvider) {
    this.probeProvider = probeProvider;
    this.propsProvider = propsProvider;
  }

  @Override
  public Health health() {
    GlobalManager gm = GlobalManager.globalManager();
    Health.Builder builder = Health.up()
            .withDetail("processes", gm.processNames().size())
            .withDetail("transactions", gm.transactionNames().size())
            .withDetail("helpers", gm.helperNames().size());

    TemporalHealthProbe probe = probeProvider == null ? null : probeProvider.getIfAvailable();
    if (probe != null) {
      TemporalHealth reachability = probe.probe();
      Map<String, Object> temporal = new LinkedHashMap<>();
      temporal.put("reachable", reachability.reachable());
      temporal.put("target", reachability.target());
      temporal.put("configuredTaskQueues", configuredTaskQueues());
      if (reachability.error() != null) {
        temporal.put("error", reachability.error());
      }
      builder.withDetail("temporal", temporal);

      if (!reachability.reachable()) {
        CbsHealthProperties props = propsProvider == null ? null : propsProvider.getIfAvailable();
        CbsHealthProperties.FailStatus failStatus = props == null
                ? CbsHealthProperties.FailStatus.NONE
                : props.temporal().failStatus();
        if (failStatus == CbsHealthProperties.FailStatus.DOWN) {
          return builder.down().build();
        }
      }
    }

    return builder.build();
  }

  private static List<String> configuredTaskQueues() {
    return GlobalManager.globalManager().generatedProcesses().stream()
            .filter(descriptor -> descriptor.type() == DslType.PROCESS)
            .map(GeneratedClassDescriptor::taskQueue)
            .distinct()
            .sorted()
            .toList();
  }
}
