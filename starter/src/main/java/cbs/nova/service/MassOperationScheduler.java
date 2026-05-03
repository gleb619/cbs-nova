package cbs.nova.service;

import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.TriggerDefinition;
import cbs.nova.model.MassOperationTriggerRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MassOperationScheduler {

  private final MassOperationService massOperationService;

  @Scheduled(fixedDelay = 60_000)
  public void pollAndTrigger() {
    log.debug("Polling for scheduled mass operations");

    Map<String, MassOperationDefinition> allMassOps =
        massOperationService.getAllMassOpDefinitions();

    for (Map.Entry<String, MassOperationDefinition> entry : allMassOps.entrySet()) {
      String code = entry.getKey();
      MassOperationDefinition massOpDef = entry.getValue();

      try {
        for (TriggerDefinition trigger : massOpDef.getTriggers()) {
          boolean shouldFire = false;
          String triggerType;

          if (trigger instanceof TriggerDefinition.CronTrigger cronTrigger) {
            shouldFire = massOperationService.shouldFireCron(cronTrigger);
            triggerType = "CRON";
          } else if (trigger instanceof TriggerDefinition.EveryTrigger everyTrigger) {
            shouldFire = massOperationService.shouldFireEvery(everyTrigger, code);
            triggerType = "SCHEDULED";
          } else {
            // OnceTrigger and SignalTrigger are ignored by the scheduler
            continue;
          }

          if (shouldFire && !massOperationService.hasRunningExecution(code)) {
            MassOperationTriggerRequest request = new MassOperationTriggerRequest(
                code, "SCHEDULER", "latest", null, triggerType, "SCHEDULER");
            massOperationService.trigger(request);
            log.info("Auto-triggered mass operation {} via {}", code, triggerType);
          }
        }
      } catch (Exception e) {
        log.error("Error triggering mass operation {}: {}", code, e.getMessage(), e);
      }
    }
  }
}
