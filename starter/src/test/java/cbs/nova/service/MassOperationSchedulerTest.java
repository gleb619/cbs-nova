package cbs.nova.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.TriggerDefinition;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class MassOperationSchedulerTest {

  @Mock
  private MassOperationService massOperationService;

  @InjectMocks
  private MassOperationScheduler scheduler;

  @Test
  @DisplayName("shouldTriggerCronMassOpWhenCronFiresAndNoRunningExecution")
  void shouldTriggerCronMassOpWhenCronFiresAndNoRunningExecution() {
    MassOperationDefinition massOpDef = mock(MassOperationDefinition.class);
    TriggerDefinition.CronTrigger cronTrigger = mock(TriggerDefinition.CronTrigger.class);
    when(massOpDef.getTriggers()).thenReturn(List.of(cronTrigger));

    when(massOperationService.getAllMassOpDefinitions())
        .thenReturn(Map.of("DAILY_INTEREST", massOpDef));
    when(massOperationService.shouldFireCron(cronTrigger)).thenReturn(true);
    when(massOperationService.hasRunningExecution("DAILY_INTEREST")).thenReturn(false);

    scheduler.pollAndTrigger();

    verify(massOperationService).trigger(any());
  }

  @Test
  @DisplayName("shouldNotTriggerCronMassOpWhenRunningExecutionExists")
  void shouldNotTriggerCronMassOpWhenRunningExecutionExists() {
    MassOperationDefinition massOpDef = mock(MassOperationDefinition.class);
    TriggerDefinition.CronTrigger cronTrigger = mock(TriggerDefinition.CronTrigger.class);
    when(massOpDef.getTriggers()).thenReturn(List.of(cronTrigger));

    when(massOperationService.getAllMassOpDefinitions())
        .thenReturn(Map.of("DAILY_INTEREST", massOpDef));
    when(massOperationService.shouldFireCron(cronTrigger)).thenReturn(true);
    when(massOperationService.hasRunningExecution("DAILY_INTEREST")).thenReturn(true);

    scheduler.pollAndTrigger();

    verify(massOperationService, never()).trigger(any());
  }

  @Test
  @DisplayName("shouldTriggerEveryMassOpWhenIntervalElapsed")
  void shouldTriggerEveryMassOpWhenIntervalElapsed() {
    MassOperationDefinition massOpDef = mock(MassOperationDefinition.class);
    TriggerDefinition.EveryTrigger everyTrigger = mock(TriggerDefinition.EveryTrigger.class);
    when(massOpDef.getTriggers()).thenReturn(List.of(everyTrigger));

    when(massOperationService.getAllMassOpDefinitions())
        .thenReturn(Map.of("HOURLY_REPORT", massOpDef));
    when(massOperationService.shouldFireEvery(everyTrigger, "HOURLY_REPORT")).thenReturn(true);
    when(massOperationService.hasRunningExecution("HOURLY_REPORT")).thenReturn(false);

    scheduler.pollAndTrigger();

    verify(massOperationService).trigger(any());
  }

  @Test
  @DisplayName("shouldNotTriggerEveryMassOpWhenIntervalNotElapsed")
  void shouldNotTriggerEveryMassOpWhenIntervalNotElapsed() {
    MassOperationDefinition massOpDef = mock(MassOperationDefinition.class);
    TriggerDefinition.EveryTrigger everyTrigger = mock(TriggerDefinition.EveryTrigger.class);
    when(massOpDef.getTriggers()).thenReturn(List.of(everyTrigger));

    when(massOperationService.getAllMassOpDefinitions())
        .thenReturn(Map.of("HOURLY_REPORT", massOpDef));
    when(massOperationService.shouldFireEvery(everyTrigger, "HOURLY_REPORT")).thenReturn(false);

    scheduler.pollAndTrigger();

    verify(massOperationService, never()).trigger(any());
  }

  @Test
  @DisplayName("shouldIgnoreOnceTriggerAndSignalTrigger")
  void shouldIgnoreOnceTriggerAndSignalTrigger() {
    MassOperationDefinition massOpDef = mock(MassOperationDefinition.class);
    TriggerDefinition.OnceTrigger onceTrigger = mock(TriggerDefinition.OnceTrigger.class);
    TriggerDefinition.SignalTrigger signalTrigger = mock(TriggerDefinition.SignalTrigger.class);
    when(massOpDef.getTriggers()).thenReturn(List.of(onceTrigger, signalTrigger));

    when(massOperationService.getAllMassOpDefinitions()).thenReturn(Map.of("ONE_TIME", massOpDef));

    scheduler.pollAndTrigger();

    verify(massOperationService, never()).trigger(any());
  }

  @Test
  @DisplayName("shouldContinuePollingWhenSingleMassOpTriggerFails")
  void shouldContinuePollingWhenSingleMassOpTriggerFails() {
    MassOperationDefinition failingDef = mock(MassOperationDefinition.class);
    TriggerDefinition.CronTrigger cronTrigger = mock(TriggerDefinition.CronTrigger.class);
    when(failingDef.getTriggers()).thenReturn(List.of(cronTrigger));

    MassOperationDefinition successDef = mock(MassOperationDefinition.class);
    TriggerDefinition.EveryTrigger everyTrigger = mock(TriggerDefinition.EveryTrigger.class);
    when(successDef.getTriggers()).thenReturn(List.of(everyTrigger));

    when(massOperationService.getAllMassOpDefinitions())
        .thenReturn(Map.of("FAILING_OP", failingDef, "SUCCESS_OP", successDef));
    when(massOperationService.shouldFireCron(cronTrigger)).thenReturn(true);
    when(massOperationService.hasRunningExecution("FAILING_OP")).thenReturn(false);
    when(massOperationService.trigger(any()))
        .thenThrow(new RuntimeException("Temporal unavailable"));
    when(massOperationService.shouldFireEvery(everyTrigger, "SUCCESS_OP")).thenReturn(true);
    when(massOperationService.hasRunningExecution("SUCCESS_OP")).thenReturn(false);

    scheduler.pollAndTrigger();

    // Should have attempted to trigger both — the exception from FAILING_OP should not stop
    // SUCCESS_OP
    verify(massOperationService, Mockito.times(2)).trigger(any());
  }

  @Test
  @DisplayName("shouldDoNothingWhenNoMassOpsDefined")
  void shouldDoNothingWhenNoMassOpsDefined() {
    when(massOperationService.getAllMassOpDefinitions()).thenReturn(Map.of());

    scheduler.pollAndTrigger();

    verify(massOperationService, never()).trigger(any());
  }
}
