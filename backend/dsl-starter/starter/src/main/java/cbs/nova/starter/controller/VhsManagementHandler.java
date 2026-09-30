package cbs.nova.starter.controller;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.controller.Pagination;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.vhs.loadtest.VhsLoadTest;
import cbs.nova.starter.vhs.loadtest.VhsLoadTestException;
import cbs.nova.starter.vhs.loadtest.VhsLoadTestReport;
import cbs.nova.starter.vhs.loadtest.VhsLoadTestRequest;
import cbs.nova.starter.vhs.management.ReplayRunRequest;
import cbs.nova.starter.vhs.management.ReplayRunResponse;
import cbs.nova.starter.vhs.management.TapeSummary;
import cbs.nova.starter.vhs.management.VhsManagementService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Functional handler for VHS tape management.
 *
 * <p>
 * Exposes list, download, delete and replay operations for recorded tapes. Registered as a
 * {@code RouterFunction} bean by
 * {@link cbs.nova.starter.config.router.VhsManagementRouterConfiguration}.
 *
 * <p>
 * Audit: destructive / fan-out mutations ({@code delete}, {@code replay}, {@code loadtest}) write
 * {@code VHS_TAPE_*} rows via {@link DslAuditService} (T776). The recorded {@code target} is the
 * tape {@code runId} (or {@code "-"} when the body parses without one); details only ever carry
 * publicly safe fields ({@code runId}, {@code replayRunId}, {@code target}, {@code concurrency},
 * etc.) and never the replayed event payloads themselves.
 */
@Slf4j
@AllArgsConstructor
public class VhsManagementHandler {

  static final String CONTENT_TYPE_X_JSONL = "application/x-jsonl";

  static final String ACTION_VHS_TAPE_DELETE = "VHS_TAPE_DELETE";
  static final String ACTION_VHS_TAPE_REPLAY = "VHS_TAPE_REPLAY";
  static final String ACTION_VHS_LOAD_TEST = "VHS_LOAD_TEST";

  private final VhsManagementService service;
  @Nullable
  private final VhsLoadTest loadTest;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<DslAuditService> auditServiceProvider;

  @NonNull
  public ServerResponse list(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", StarterConstants.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", StarterConstants.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);

    List<TapeSummary> tapes = service.listTapes();
    long total = tapes.size();
    List<TapeSummary> paged = tapes.stream()
            .skip(skip)
            .limit(pageSize)
            .toList();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new PageResponse<>(paged, total, skip, pageSize));
  }

  @NonNull
  public ServerResponse download(ServerRequest request) {
    String runId = request.pathVariable("runId");
    Optional<InputStream> stream = service.openTapeStream(runId);
    if (stream.isEmpty()) {
      return notFound("Tape not found: " + runId);
    }
    try {
      String body = new String(stream.get().readAllBytes(), StandardCharsets.UTF_8);
      return ServerResponse.ok()
              .contentType(MediaType.parseMediaType(CONTENT_TYPE_X_JSONL))
              .body(body);
    } catch (IOException ex) {
      log.warn("[VHS] failed to read tape for runId={}: {}", runId, ex.getMessage());
      return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body(new ErrorResponse("TAPE_READ_ERROR", "Failed to read tape", null, null, null,
                      null, null, null, null));
    }
  }

  @NonNull
  public ServerResponse delete(ServerRequest request) {
    String runId = request.pathVariable("runId");
    boolean deleted = service.deleteTape(runId);
    String outcome = deleted ? StarterConstants.OUTCOME_SUCCESS : StarterConstants.OUTCOME_FAILURE;
    String errorMessage = deleted ? null : "tape_not_found";
    Map<String, Object> details = deleted
            ? Map.of("runId", runId)
            : Map.of("runId", runId, "error", errorMessage);
    audit(request, ACTION_VHS_TAPE_DELETE, runId, outcome, details);
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("deleted", deleted));
  }

  @NonNull
  public ServerResponse replay(ServerRequest request) throws IOException {
    String runId = request.pathVariable("runId");
    ReplayRunRequest body = parse(request, ReplayRunRequest.class);
    if (body == null) {
      body = new ReplayRunRequest(null, null, null, null, null);
    }

    Optional<ReplayRunResponse> response = service.startReplay(runId, body);
    if (response.isEmpty()) {
      audit(request, ACTION_VHS_TAPE_REPLAY, runId, StarterConstants.OUTCOME_FAILURE,
              Map.of("runId", runId, "error", "replay_unavailable"));
      return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("error", "VHS replay is not enabled"));
    }
    ReplayRunResponse r = response.get();
    HttpStatus status = "accepted".equals(r.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
    String outcome = "accepted".equals(r.status())
            ? StarterConstants.OUTCOME_SUCCESS
            : StarterConstants.OUTCOME_FAILURE;
    audit(request, ACTION_VHS_TAPE_REPLAY, runId, outcome,
            Map.of("runId", runId, "replayRunId", r.replayRunId(), "target",
                    String.valueOf(r.target()), "status", String.valueOf(r.status())));
    return ServerResponse.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(r);
  }

  @NonNull
  public ServerResponse loadtest(ServerRequest request) throws IOException {
    if (loadTest == null) {
      audit(request, ACTION_VHS_LOAD_TEST, "-", StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "loadtest_disabled"));
      return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("error",
                      "VHS load-test is not enabled (cbs.vhs.replay.enabled must be true)"));
    }

    VhsLoadTestRequest body = parse(request, VhsLoadTestRequest.class);
    if (body == null) {
      body = new VhsLoadTestRequest(null, null, null, null, null);
    }

    String tapes = body.tapes() != null ? body.tapes() : "**/*.vhs.jsonl";
    String target = body.target() != null ? body.target() : "dry-run";
    double speed = body.speed() != null ? body.speed() : 1.0;
    int concurrency = body.concurrency() != null ? body.concurrency() : 4;
    long duration = body.duration() != null ? body.duration() : 0;

    log.warn("[VHS load-test] request: target={} speed={} concurrency={} duration={} tapes={}",
            target, speed, concurrency, duration, tapes);

    try {
      VhsLoadTestReport report = loadTest.run(tapes, target, speed, concurrency, duration);
      audit(request, ACTION_VHS_LOAD_TEST, "-", StarterConstants.OUTCOME_SUCCESS,
              Map.of("target", target, "concurrency", concurrency, "duration", duration,
                      "tapesLoaded", report.tapesLoaded()));
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(report);
    } catch (VhsLoadTestException ex) {
      log.warn("[VHS load-test] failed: {}", ex.getMessage());
      audit(request, ACTION_VHS_LOAD_TEST, "-", StarterConstants.OUTCOME_FAILURE,
              Map.of("target", target, "error", String.valueOf(ex.getMessage())));
      return ServerResponse.status(HttpStatus.BAD_REQUEST)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new ErrorResponse("VHS_LOAD_TEST_ERROR", ex.getMessage(), null, null, null,
                      null, null, null, null));
    }
  }

  private <T> T parse(ServerRequest request, Class<T> type) throws IOException {
    try {
      String body = request.body(String.class);
      if (body == null || body.isBlank()) {
        return null;
      }
      return objectMapper.readValue(body, type);
    } catch (JacksonException e) {
      log.warn("[VHS replay] failed to parse request body: {}", e.getMessage());
      return null;
    } catch (Exception e) {
      throw new IOException("Failed to read request body", e);
    }
  }

  private static ServerResponse notFound(String message) {
    return ServerResponse.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ErrorResponse("NOT_FOUND", message, null, null, null, null, null, null,
                    null));
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
}
