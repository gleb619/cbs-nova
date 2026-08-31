package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.exception.DefinitionNotFoundException;
import cbs.nova.starter.exception.ScheduleConflictException;
import cbs.nova.starter.model.ScheduleModels.CreateScheduleRequest;
import cbs.nova.starter.model.ScheduleModels.ScheduleSummary;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleDescription;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleInfo;
import io.temporal.client.schedules.ScheduleListDescription;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.client.schedules.ScheduleState;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Stream;

class DslScheduleServiceTest {

  private final ScheduleClient scheduleClient = mock(ScheduleClient.class);
  private final ScheduleHandle handle = mock(ScheduleHandle.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DslScheduleService service = new DslScheduleService(scheduleClient, objectMapper);

  @BeforeEach
  void setUp() {
    GeneratedClassDescriptor descriptor = new GeneratedClassDescriptor(
            "LoanDisbursement",
            DslType.PROCESS,
            "1.0.0",
            "dsl-task-queue",
            TestWorkflow.class,
            TestWorkflowImpl.class,
            null,
            null,
            "{}");
    GlobalManager.globalManager().registerGeneratedClass(descriptor);
  }

  @Test
  void scheduleIdForAddsPrefixAndValidatesName() {
    assertThat(service.scheduleIdFor("LoanDisbursement")).isEqualTo("sched-LoanDisbursement");
  }

  @Test
  void scheduleIdForRejectsUnsafeNames() {
    assertThatThrownBy(() -> service.scheduleIdFor("bad/name"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid definition name");
  }

  @Test
  void createSucceedsForPublishedDefinitionAndValidCron() {
    when(scheduleClient.createSchedule(eq("sched-LoanDisbursement"), any(Schedule.class),
            any(ScheduleOptions.class)))
            .thenReturn(handle);

    var response = service
            .create(new CreateScheduleRequest("LoanDisbursement", "0 9 * * *", "UTC", null, null));

    assertThat(response.scheduleId()).isEqualTo("sched-LoanDisbursement");
    assertThat(response.definition()).isEqualTo("LoanDisbursement");
    assertThat(response.cron()).isEqualTo("0 9 * * *");
    verify(scheduleClient).createSchedule(eq("sched-LoanDisbursement"), any(Schedule.class),
            any(ScheduleOptions.class));
  }

  @Test
  void createDefaultsTimezoneToUtc() {
    when(scheduleClient.createSchedule(eq("sched-LoanDisbursement"), any(), any()))
            .thenReturn(handle);

    service.create(new CreateScheduleRequest("LoanDisbursement", "0 9 * * *", null, null, null));

    verify(scheduleClient).createSchedule(eq("sched-LoanDisbursement"), any(), any());
  }

  @Test
  void createThrowsDefinitionNotFoundForUnknownDefinition() {
    assertThatThrownBy(() -> service
            .create(new CreateScheduleRequest("Missing", "0 9 * * *", "UTC", null, null)))
            .isInstanceOf(DefinitionNotFoundException.class)
            .hasMessageContaining("No published definition: Missing");
    verify(scheduleClient, never()).createSchedule(any(), any(), any());
  }

  @Test
  void createThrowsIllegalArgumentForBlankCron() {
    assertThatThrownBy(() -> service
            .create(new CreateScheduleRequest("LoanDisbursement", "   ", "UTC", null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cron is required");
  }

  @Test
  void createThrowsIllegalArgumentForBadTimezone() {
    assertThatThrownBy(() -> service.create(
            new CreateScheduleRequest("LoanDisbursement", "0 9 * * *", "Mars/Phobos", null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid timezone");
  }

  @Test
  void createThrowsScheduleConflictWhenAlreadyRunning() {
    when(scheduleClient.createSchedule(eq("sched-LoanDisbursement"), any(), any()))
            .thenThrow(
                    new ScheduleAlreadyRunningException(new RuntimeException("already running")));

    assertThatThrownBy(() -> service
            .create(new CreateScheduleRequest("LoanDisbursement", "0 9 * * *", "UTC", null, null)))
            .isInstanceOf(ScheduleConflictException.class)
            .hasMessageContaining("Schedule already exists: sched-LoanDisbursement");
  }

  @Test
  void listFiltersAndMapsOnlySchedPrefixedIds() {
    ScheduleListDescription ours = mock(ScheduleListDescription.class);
    when(ours.getScheduleId()).thenReturn("sched-LoanDisbursement");
    ScheduleListDescription other = mock(ScheduleListDescription.class);
    when(other.getScheduleId()).thenReturn("foreign-schedule");
    when(scheduleClient.listSchedules()).thenReturn(Stream.of(ours, other));

    ScheduleDescription description = description("sched-LoanDisbursement", "0 9 * * *",
            "Asia/Almaty", "note-1", false);
    when(scheduleClient.getHandle("sched-LoanDisbursement")).thenReturn(handle);
    when(handle.describe()).thenReturn(description);

    List<ScheduleSummary> result = service.list();

    assertThat(result).hasSize(1);
    ScheduleSummary summary = result.get(0);
    assertThat(summary.scheduleId()).isEqualTo("sched-LoanDisbursement");
    assertThat(summary.definition()).isEqualTo("LoanDisbursement");
    assertThat(summary.cron()).isEqualTo("0 9 * * *");
    assertThat(summary.timezone()).isEqualTo("Asia/Almaty");
    assertThat(summary.note()).isEqualTo("note-1");
    assertThat(summary.paused()).isFalse();
  }

  @Test
  void deleteInvokesHandleDelete() {
    when(scheduleClient.getHandle("sched-LoanDisbursement")).thenReturn(handle);

    service.delete("LoanDisbursement");

    verify(handle).delete();
  }

  @Test
  void deleteSwallowsNotFoundAsNoOp() {
    when(scheduleClient.getHandle("sched-LoanDisbursement")).thenReturn(handle);
    doThrow(new StatusRuntimeException(Status.NOT_FOUND)).when(handle).delete();

    assertThatNoExceptionThrown(() -> service.delete("LoanDisbursement"));

    verify(handle).delete();
  }

  private static ScheduleDescription description(String scheduleId, String cron, String timezone,
          String note, boolean paused) {
    ScheduleSpec spec = mock(ScheduleSpec.class);
    when(spec.getCronExpressions()).thenReturn(List.of(cron));
    when(spec.getTimeZoneName()).thenReturn(timezone);
    ScheduleState state = mock(ScheduleState.class);
    when(state.getNote()).thenReturn(note);
    when(state.isPaused()).thenReturn(paused);
    Schedule schedule = mock(Schedule.class);
    when(schedule.getSpec()).thenReturn(spec);
    when(schedule.getState()).thenReturn(state);
    ScheduleInfo info = mock(ScheduleInfo.class);
    when(info.getNextActionTimes()).thenReturn(List.of());
    ScheduleDescription description = mock(ScheduleDescription.class);
    when(description.getId()).thenReturn(scheduleId);
    when(description.getSchedule()).thenReturn(schedule);
    when(description.getInfo()).thenReturn(info);
    return description;
  }

  private static void assertThatNoExceptionThrown(Runnable runnable) {
    try {
      runnable.run();
    } catch (Exception e) {
      throw new AssertionError("Expected no exception but got: " + e, e);
    }
  }

  @WorkflowInterface
  public interface TestWorkflow {
    @WorkflowMethod
    String execute(Object request);
  }

  public static class TestWorkflowImpl implements TestWorkflow {
    @Override
    public String execute(Object request) {
      return "ok";
    }
  }
}
