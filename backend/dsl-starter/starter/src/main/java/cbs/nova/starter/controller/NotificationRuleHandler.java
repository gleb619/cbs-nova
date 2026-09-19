package cbs.nova.starter.controller;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.entity.NotificationRuleFiringEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.ChannelDto;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.model.NotificationRuleModels.NotificationRuleDto;
import cbs.nova.starter.model.NotificationRuleModels.TestEventRequest;
import cbs.nova.starter.model.NotificationRuleModels.TestEventResponse;
import cbs.nova.starter.model.NotificationRuleModels.ToggleEnabledRequest;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.notification.NotificationRuleEngine;
import cbs.nova.starter.notification.NotificationRuleService;
import cbs.nova.starter.persistence.NotificationRuleFiringRepository;
import cbs.nova.starter.persistence.NotificationRuleFiringSearchResult;
import cbs.nova.starter.persistence.NotificationRuleSearchResult;
import cbs.nova.starter.webhook.WebhookDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.JacksonException;

/**
 * Functional handler for the T565 notification rules engine: CRUD over
 * {@code dsl_notification_rule}, the channel catalogue, the firing audit log, and a synthetic
 * synchronous test path. Thin HTTP shell around {@link NotificationRuleService} /
 * {@link NotificationRuleEngine}.
 *
 * <p>
 * Secrets are write-only: {@link #redact} nulls {@code action.secret} on every response.
 */
@Tag(name = "Notifications", description = "Notification rules engine (T565)")
@RequiredArgsConstructor
public class NotificationRuleHandler {

  private final NotificationRuleService ruleService;
  private final NotificationRuleEngine ruleEngine;
  private final NotificationRuleFiringRepository firingRepository;
  private final Optional<WebhookDispatcher> webhookDispatcher;

  @Operation(summary = "List notification rules, match order (priority desc)")
  @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class)))
  public ServerResponse list(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", StarterConstants.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", StarterConstants.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);

    NotificationRuleSearchResult result = ruleService.list(skip, pageSize);
    List<NotificationRuleDto> items = result.items().stream().map(this::toDto).toList();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new PageResponse<>(items, result.total(), skip, pageSize));
  }

  @Operation(summary = "Create a notification rule")
  @ApiResponse(responseCode = "201", description = "Rule created")
  public ServerResponse create(ServerRequest request) throws ServletException, IOException {
    NotificationRuleDto created = toDto(ruleService.create(parseRuleBody(request)));
    return ServerResponse.status(HttpStatus.CREATED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(created);
  }

  @Operation(summary = "Get a notification rule by id")
  @ApiResponse(responseCode = "200", description = "The rule")
  @ApiResponse(responseCode = "404", description = "No rule with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  public ServerResponse get(ServerRequest request) {
    long id = Long.parseLong(request.pathVariable("id"));
    return ruleService.findById(id)
            .map(rule -> ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toDto(rule)))
            .orElseGet(() -> notFound(id));
  }

  @Operation(summary = "Replace a notification rule")
  @ApiResponse(responseCode = "200", description = "The updated rule")
  @ApiResponse(responseCode = "404", description = "No rule with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  public ServerResponse update(ServerRequest request) throws ServletException, IOException {
    long id = Long.parseLong(request.pathVariable("id"));
    NotificationRuleDto body = parseRuleBody(request);
    return ruleService.update(id, body)
            .map(rule -> ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toDto(rule)))
            .orElseGet(() -> notFound(id));
  }

  @Operation(summary = "Delete a notification rule")
  @ApiResponse(responseCode = "204", description = "Rule deleted")
  @ApiResponse(responseCode = "404", description = "No rule with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  public ServerResponse delete(ServerRequest request) {
    long id = Long.parseLong(request.pathVariable("id"));
    if (!ruleService.delete(id)) {
      return notFound(id);
    }
    return ServerResponse.noContent().build();
  }

  @Operation(summary = "Enable or disable a notification rule")
  @ApiResponse(responseCode = "200", description = "The updated rule")
  @ApiResponse(responseCode = "404", description = "No rule with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  public ServerResponse setEnabled(ServerRequest request) throws ServletException, IOException {
    long id = Long.parseLong(request.pathVariable("id"));
    ToggleEnabledRequest body;
    try {
      body = request.body(ToggleEnabledRequest.class);
    } catch (JacksonException | IllegalStateException ex) {
      throw new IllegalArgumentException("malformed JSON body: " + ex.getMessage(), ex);
    }
    return ruleService.setEnabled(id, body.enabled())
            .map(rule -> ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toDto(rule)))
            .orElseGet(() -> notFound(id));
  }

  @Operation(summary = "List available notification channel types")
  @ApiResponse(responseCode = "200", description = "Static channel catalogue; email is a placeholder")
  public ServerResponse channels(ServerRequest request) {
    List<ChannelDto> channels = List.of(
            new ChannelDto("webhook", webhookDispatcher.isPresent(), false),
            new ChannelDto("email", true, true),
            new ChannelDto("slack", true, false),
            new ChannelDto("pagerduty", true, false));
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("items", channels));
  }

  @Operation(summary = "List notification rule firing audit rows, newest first")
  @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class)))
  public ServerResponse fireLog(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", StarterConstants.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", StarterConstants.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);
    Long ruleId = request.param("ruleId")
            .filter(s -> !s.isBlank())
            .map(NotificationRuleHandler::parseLongParam)
            .orElse(null);

    NotificationRuleFiringSearchResult result = firingRepository.search(ruleId, skip, pageSize);
    List<NotificationRuleFiringEntity> items = result.items();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new PageResponse<>(items, result.total(), skip, pageSize));
  }

  @Operation(summary = "Test-match a synthetic event against the enabled rules and report results")
  @ApiResponse(responseCode = "200", description = "Matched rule ids and per-action results; nothing is persisted")
  public ServerResponse test(ServerRequest request) throws ServletException, IOException {
    TestEventRequest body;
    try {
      body = request.body(TestEventRequest.class);
    } catch (JacksonException | IllegalStateException ex) {
      throw new IllegalArgumentException("malformed JSON body: " + ex.getMessage(), ex);
    }
    if (body.eventType() == null || body.eventType().isBlank()) {
      throw new IllegalArgumentException("'eventType' is required and must not be blank");
    }
    DomainEvent event = syntheticEvent(body);
    TestEventResponse response = event != null
            ? ruleEngine.testEvent(event)
            : new TestEventResponse(List.of(), List.of());
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(response);
  }

  /**
   * Builds a best-effort synthetic run-lifecycle event for the test path. Returns {@code null} for
   * event types that are not run-lifecycle (the engine only matches events it can construct;
   * {@code DomainEvent} is sealed so no generic stand-in exists).
   */
  private static @Nullable DomainEvent syntheticEvent(TestEventRequest body) {
    String runId = body.runId() != null && !body.runId().isBlank() ? body.runId() : "test-run";
    String processName = body.processName() != null && !body.processName().isBlank()
            ? body.processName()
            : "TestProcess";
    Instant now = Instant.now();
    return switch (body.eventType()) {
      case "RunStarted" -> new DomainEvent.RunStarted(runId, processName, "test", now, null);
      case "RunCompleted" -> new DomainEvent.RunCompleted(runId, processName,
              DslRunStatus.COMPLETED, null, null, now, now, now, null);
      case "RunFailed" -> new DomainEvent.RunFailed(runId, processName, DslRunStatus.FAILED,
              "synthetic test event", now, now, now, null);
      case "RunCancelled" -> new DomainEvent.RunCancelled(runId, processName,
              DslRunStatus.CANCELLED, "synthetic test event", now, now, now, null);
      case "RunStale" -> new DomainEvent.RunStale(runId, processName, DslRunStatus.STALE,
              "synthetic test event", now, now, now, null);
      default -> null;
    };
  }

  private NotificationRuleDto toDto(NotificationRuleEntity entity) {
    return new NotificationRuleDto(
            entity.id(),
            entity.name(),
            entity.enabled(),
            ruleService.filterOf(entity),
            redact(ruleService.actionsOf(entity)),
            entity.priority(),
            entity.rateClass(),
            entity.createdAt(),
            entity.updatedAt());
  }

  private static List<NotificationActionDto> redact(List<NotificationActionDto> actions) {
    return actions.stream()
            .map(action -> new NotificationActionDto(action.sink(), action.url(), null,
                    action.to()))
            .toList();
  }

  private static NotificationRuleDto parseRuleBody(ServerRequest request)
          throws ServletException, IOException {
    try {
      NotificationRuleDto body = request.body(NotificationRuleDto.class);
      if (body == null) {
        throw new IllegalArgumentException("request body is required");
      }
      return body;
    } catch (JacksonException | IllegalStateException ex) {
      throw new IllegalArgumentException("malformed JSON body: " + ex.getMessage(), ex);
    }
  }

  private static long parseLongParam(String raw) {
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
              "Invalid value for query parameter 'ruleId': '" + raw + "' (expected an integer)");
    }
  }

  private static ServerResponse notFound(long id) {
    return ServerResponse.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ErrorResponse("NOT_FOUND", "Notification rule not found: " + id, null,
                    null, null, null, null, null, null));
  }

}
