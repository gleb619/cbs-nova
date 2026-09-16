package cbs.nova.starter.controller;

import cbs.nova.starter.config.router.DslScheduleRouterConfiguration;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.controller.Pagination;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.ScheduleModels.CreateScheduleRequest;
import cbs.nova.starter.model.ScheduleModels.ScheduleActionRequest;
import cbs.nova.starter.model.ScheduleModels.ScheduleSummary;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslScheduleService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.JacksonException;
import io.temporal.client.schedules.ScheduleClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import java.util.List;
import java.util.Map;

/**
 * Functional handler for DSL schedule CRUD. Registered as a {@code RouterFunction} bean by
 * {@link DslScheduleRouterConfiguration}.
 */
@Slf4j
@Component
@AllArgsConstructor
@ConditionalOnBean(ScheduleClient.class)
public class DslScheduleHandler {

  static final String ACTION_SCHEDULE_CREATE = "SCHEDULE_CREATE";
  static final String ACTION_SCHEDULE_DELETE = "SCHEDULE_DELETE";
  static final String ACTION_SCHEDULE_PAUSE = "schedule.paused";
  static final String ACTION_SCHEDULE_RESUME = "schedule.resumed";

  private final DslScheduleService service;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<DslAuditService> auditServiceProvider;

  public ServerResponse create(ServerRequest request) throws IOException {
    CreateScheduleRequest body = parse(request, CreateScheduleRequest.class);
    if (body == null) {
      audit(request, ACTION_SCHEDULE_CREATE, "-", StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "request body is required"));
      return badRequest("Request body is required");
    }
    try {
      var response = service.create(body);
      audit(request, ACTION_SCHEDULE_CREATE, response.scheduleId(),
              StarterConstants.OUTCOME_SUCCESS,
              Map.of("definition", String.valueOf(response.definition()),
                      "cron", String.valueOf(response.cron())));
      return ServerResponse.status(HttpStatus.CREATED)
              .contentType(MediaType.APPLICATION_JSON)
              .body(response);
    } catch (RuntimeException e) {
      audit(request, ACTION_SCHEDULE_CREATE, String.valueOf(body.definition()),
              StarterConstants.OUTCOME_FAILURE, Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  public ServerResponse list(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", StarterConstants.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", StarterConstants.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);

    List<ScheduleSummary> schedules = service.list();
    long total = schedules.size();
    List<ScheduleSummary> paged = schedules.stream()
            .skip(skip)
            .limit(pageSize)
            .toList();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new PageResponse<>(paged, total, skip, pageSize));
  }

  public ServerResponse delete(ServerRequest request) {
    String definition = request.pathVariable("definition");
    try {
      service.delete(definition);
      audit(request, ACTION_SCHEDULE_DELETE, definition, StarterConstants.OUTCOME_SUCCESS, null);
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("deleted", true));
    } catch (RuntimeException e) {
      audit(request, ACTION_SCHEDULE_DELETE, definition, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  public ServerResponse pause(ServerRequest request) throws IOException {
    String definition = request.pathVariable("definition");
    ScheduleActionRequest body = parse(request, ScheduleActionRequest.class);
    String reason = body != null ? body.reason() : null;
    try {
      service.pause(definition, reason);
      audit(request, ACTION_SCHEDULE_PAUSE, definition, StarterConstants.OUTCOME_SUCCESS,
              Map.of("definition", definition));
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("paused", true));
    } catch (RuntimeException e) {
      audit(request, ACTION_SCHEDULE_PAUSE, definition, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  public ServerResponse resume(ServerRequest request) throws IOException {
    String definition = request.pathVariable("definition");
    ScheduleActionRequest body = parse(request, ScheduleActionRequest.class);
    String reason = body != null ? body.reason() : null;
    try {
      service.resume(definition, reason);
      audit(request, ACTION_SCHEDULE_RESUME, definition, StarterConstants.OUTCOME_SUCCESS,
              Map.of("definition", definition));
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("resumed", true));
    } catch (RuntimeException e) {
      audit(request, ACTION_SCHEDULE_RESUME, definition, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  private void audit(ServerRequest request, String action, String target, String outcome,
          Object details) {
    if (auditServiceProvider == null) {
      return;
    }
    var auditService = auditServiceProvider.getIfAvailable();
    if (auditService == null) {
      return;
    }
    auditService.record(DslAuditService.currentActor(), action, target,
            DslAuditService.correlationIdOf(request), outcome, details);
  }

  private <T> T parse(ServerRequest request, Class<T> type) throws IOException {
    try {
      String body = request.body(String.class);
      if (body == null || body.isBlank()) {
        return null;
      }
      return objectMapper.readValue(body, type);
    } catch (JacksonException e) {
      log.warn("[DSL schedules] failed to parse request body: {}", e.getMessage());
      return null;
    } catch (Exception e) {
      throw new IOException("Failed to read request body", e);
    }
  }

  private static ServerResponse badRequest(String message) {
    return ServerResponse.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("BAD_REQUEST", message, null, null, null, null, null, null,
                    null));
  }
}
