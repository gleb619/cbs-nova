package cbs.nova.starter.service;

import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.process.DslTemporalProcessRequest;
import cbs.nova.starter.exception.DefinitionNotFoundException;
import cbs.nova.starter.exception.ScheduleConflictException;
import cbs.nova.starter.model.ScheduleModels.CreateScheduleRequest;
import cbs.nova.starter.model.ScheduleModels.CreateScheduleResponse;
import cbs.nova.starter.model.ScheduleModels.ScheduleSummary;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleDescription;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleInfo;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.SchedulePolicy;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.client.schedules.ScheduleException;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Service for creating, listing and deleting Temporal Schedules that start DSL process workflows
 * directly via {@link ScheduleActionStartWorkflow}.
 *
 * <p>
 * The bean only loads when a {@link ScheduleClient} is available, keeping the schedule surface
 * absent in non-Temporal deployments.
 */
@Slf4j
@Component
@ConditionalOnBean(ScheduleClient.class)
@RequiredArgsConstructor
public class DslScheduleService {

  private static final String SCHEDULE_PREFIX = "sched-";
  private static final String DEFAULT_TIMEZONE = "UTC";
  private static final Pattern DEFINITION_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,120}$");

  private final ScheduleClient scheduleClient;
  private final ObjectMapper objectMapper;

  /**
   * Returns the schedule id we use for a given definition name.
   *
   * <p>
   * Validates that the definition name contains only safe characters so it can be embedded in a
   * Temporal schedule id without escaping surprises.
   */
  public @NonNull String scheduleIdFor(@NonNull String definition) {
    if (!DEFINITION_NAME_PATTERN.matcher(definition).matches()) {
      throw new IllegalArgumentException(
              "Invalid definition name: must match " + DEFINITION_NAME_PATTERN.pattern());
    }
    return SCHEDULE_PREFIX + definition;
  }

  /**
   * Creates a Temporal schedule that fires the definition's workflow on the given cron.
   *
   * <p>
   * Each fire starts a fresh workflow execution. We deliberately do not set a fixed workflow id in
   * {@link WorkflowOptions}, so Temporal assigns a per-fire id of the form
   * {@code <scheduleId>-<scheduled-time>}.
   */
  public CreateScheduleResponse create(CreateScheduleRequest request) {
    String definition = requireNonBlank(request.definition(), "definition is required");
    String cron = requireNonBlank(request.cron(), "cron is required");

    GeneratedClassDescriptor descriptor = GlobalManager.globalManager()
            .findGeneratedProcess(definition)
            .orElseThrow(() -> new DefinitionNotFoundException(definition));

    String timezone = request.timezone() != null && !request.timezone().isBlank()
            ? request.timezone()
            : DEFAULT_TIMEZONE;
    try {
      ZoneId.of(timezone);
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("Invalid timezone: " + timezone, e);
    }

    Object input = request.input() != null ? request.input() : Map.of();
    String scheduleId = scheduleIdFor(definition);

    ScheduleActionStartWorkflow action = ScheduleActionStartWorkflow.newBuilder()
            .setWorkflowType(descriptor.temporalInterface())
            .setArguments(new DslTemporalProcessRequest<>("scheduled", input))
            .setOptions(WorkflowOptions.newBuilder()
                    .setTaskQueue(descriptor.taskQueue())
                    .build())
            .build();

    ScheduleSpec spec = ScheduleSpec.newBuilder()
            .setCronExpressions(List.of(cron))
            .setTimeZoneName(timezone)
            .build();

    Schedule schedule = Schedule.newBuilder()
            .setAction(action)
            .setSpec(spec)
            .setPolicy(SchedulePolicy.newBuilder()
                    .setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP)
                    .setCatchupWindow(Duration.ofMinutes(1))
                    .build())
            .build();

    try {
      scheduleClient.createSchedule(scheduleId, schedule, ScheduleOptions.newBuilder().build());
    } catch (ScheduleAlreadyRunningException e) {
      throw new ScheduleConflictException(scheduleId);
    }

    log.info("[DSL schedules] created {} for definition '{}' with cron '{}' in timezone '{}'",
            scheduleId, definition, cron, timezone);
    return new CreateScheduleResponse(scheduleId, definition, cron);
  }

  /**
   * Lists schedules created by this service (ids prefixed with "sched-"). For each schedule,
   * describes it to obtain the cron spec, timezone, note, paused flag and next scheduled fire time.
   */
  public List<ScheduleSummary> list() {
    try (Stream<io.temporal.client.schedules.ScheduleListDescription> stream = scheduleClient
            .listSchedules()) {
      return stream
              .map(io.temporal.client.schedules.ScheduleListDescription::getScheduleId)
              .filter(id -> id.startsWith(SCHEDULE_PREFIX))
              .map(this::describe)
              .toList();
    }
  }

  /**
   * Deletes the schedule for a definition. If the schedule does not exist, the call succeeds
   * idempotently.
   */
  public void delete(@NonNull String definition) {
    String scheduleId = scheduleIdFor(definition);
    ScheduleHandle handle = scheduleClient.getHandle(scheduleId);
    try {
      handle.delete();
      log.info("[DSL schedules] deleted {}", scheduleId);
    } catch (Exception e) {
      if (!isNotFound(e)) {
        throw e;
      }
      log.info("[DSL schedules] delete of {} skipped: schedule not found", scheduleId);
    }
  }

  private ScheduleSummary describe(String scheduleId) {
    ScheduleDescription description = scheduleClient.getHandle(scheduleId).describe();
    ScheduleSpec spec = description.getSchedule().getSpec();
    ScheduleInfo info = description.getInfo();
    String cron = spec.getCronExpressions().isEmpty() ? "" : spec.getCronExpressions().get(0);
    String definition = scheduleId.substring(SCHEDULE_PREFIX.length());
    String nextRunAt = firstInstant(info.getNextActionTimes());
    return new ScheduleSummary(
            scheduleId,
            definition,
            cron,
            spec.getTimeZoneName(),
            description.getSchedule().getState().getNote(),
            nextRunAt,
            description.getSchedule().getState().isPaused());
  }

  private static @Nullable String firstInstant(List<Instant> instants) {
    return instants == null || instants.isEmpty() ? null : instants.get(0).toString();
  }

  private static boolean isNotFound(Exception e) {
    Throwable current = e;
    while (current != null) {
      if (current instanceof StatusRuntimeException sre
              && sre.getStatus().getCode() == Status.Code.NOT_FOUND) {
        return true;
      }
      if (current instanceof ScheduleException) {
        // ScheduleException wraps the underlying gRPC failure; unwrap it below.
      }
      current = current.getCause();
    }
    return false;
  }

  private static String requireNonBlank(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }
}
