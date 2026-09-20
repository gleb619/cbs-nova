package cbs.nova.starter.reporting;

import cbs.nova.starter.config.properties.CbsStartupReportProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(CbsStartupReportProperties.class)
@RequiredArgsConstructor
@Slf4j
public class StartupTimeReporter {

  private final CbsStartupReportProperties properties;

  @EventListener(ApplicationReadyEvent.class)
  public void report(ApplicationReadyEvent event) {
    if (!properties.enabled()) {
      return;
    }
    var context = event.getApplicationContext();
    log.info(
            "STARTUP_REPORT totalMillis={} beans={} profiles={};",
            event.getTimeTaken().toMillis(),
            context.getBeanDefinitionCount(),
            String.join(",", context.getEnvironment().getActiveProfiles()));
    if (context.getApplicationStartup() instanceof BufferingApplicationStartup buffering) {
      var lines = summarize(
              buffering.drainBufferedTimeline(),
              properties.topSteps(),
              Duration.ofMillis(properties.slowThresholdMillis()));

      log.info("STARTUP_REPORT \n {}\n---", String.join("\n ", lines.getFirst()));
      log.debug("STARTUP_REPORT \n {}\n---", String.join("\n ", lines.getLast()));
    }
  }

  protected List<List<String>> summarize(StartupTimeline timeline, int topSteps,
          Duration slowThreshold) {
    var step = new ArrayList<String>();
    var substep = new ArrayList<String>();
    timeline.getEvents().stream()
            .collect(
                    Collectors.groupingBy(
                            event -> event.getStartupStep().getName(),
                            Collectors.summingLong(event -> event.getDuration().toMillis())))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(topSteps)
            .forEach(e -> step.add("step=%s totalMillis=%d".formatted(e.getKey(), e.getValue())));

    timeline.getEvents().stream()
            .filter(event -> event.getDuration().compareTo(slowThreshold) >= 0)
            .sorted(Comparator.comparing(StartupTimeline.TimelineEvent::getDuration).reversed())
            .forEach(
                    event -> substep.add(
                            "slow=%s millis=%d"
                                    .formatted(
                                            event.getStartupStep().getName(),
                                            event.getDuration().toMillis())));
    return List.of(step, substep);
  }
}
