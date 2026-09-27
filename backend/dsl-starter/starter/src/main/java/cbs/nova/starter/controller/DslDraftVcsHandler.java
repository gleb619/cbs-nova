package cbs.nova.starter.controller;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.service.DomainEventPublisher;
import cbs.nova.starter.events.WorkspaceLifecycleEvent;
import cbs.nova.starter.model.VcsModels.BatchCommitRequest;
import cbs.nova.starter.model.VcsModels.CommitRequest;
import cbs.nova.starter.model.VcsModels.CommitResult;
import cbs.nova.starter.model.VcsModels.FetchResult;
import cbs.nova.starter.model.VcsModels.PullResult;
import cbs.nova.starter.model.VcsModels.RebaseResult;
import cbs.nova.starter.service.CorrelationId;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslSourcePathResolver;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Workspace-level git operations delegated to dsl-builder: fetch, pull, rebase and the batch
 * staging-area commit (§5.1 of {@code docs/backend/drafts.md}). Kept separate from
 * {@link DslDraftHandler}, which handles per-definition draft flows.
 */
@Slf4j
@Component
@AllArgsConstructor
@ConditionalOnProperty(prefix = "cbs.dsl.drafts", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DslDraftVcsHandler {

  static final String ACTION_DRAFT_VCS_FETCH = "DRAFT_VCS_FETCH";
  static final String ACTION_DRAFT_VCS_PULL = "DRAFT_VCS_PULL";
  static final String ACTION_DRAFT_VCS_REBASE = "DRAFT_VCS_REBASE";
  static final String ACTION_DRAFTS_COMMIT = "DRAFTS_COMMIT";
  static final String GIT_REQUIRES_BUILDER_CODE = "GIT_REQUIRES_BUILDER";
  static final String REPO_TARGET = "repository";

  private final DslProperties dslProperties;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<DslBuilderClient> builderClientProvider;
  private final ObjectProvider<DslAuditService> auditServiceProvider;
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;
  private final ObjectProvider<DslSourcePathResolver> sourcePathResolverProvider;

  public ServerResponse fetch(ServerRequest request) {
    var configured = ensureConfigured();
    if (configured != null) {
      return configured;
    }
    var builder = builderClient();
    if (builder == null) {
      return gitRequiresBuilder();
    }
    try {
      FetchResult result = builder.vcsFetch();
      audit(request, ACTION_DRAFT_VCS_FETCH, StarterConstants.OUTCOME_SUCCESS,
              Map.of("branch", String.valueOf(result.branch()),
                      "fetched", result.fetched(),
                      "ahead", result.ahead(),
                      "behind", result.behind()));
      publishEventBestEffort(new WorkspaceLifecycleEvent.DraftsRepoFetched(
              result.branch(), result.fetched(), result.ahead(), result.behind(), null,
              correlationIdOf(request)));
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    } catch (RuntimeException e) {
      audit(request, ACTION_DRAFT_VCS_FETCH, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  public ServerResponse pull(ServerRequest request) {
    var configured = ensureConfigured();
    if (configured != null) {
      return configured;
    }
    var builder = builderClient();
    if (builder == null) {
      return gitRequiresBuilder();
    }
    try {
      PullResult result = builder.vcsPull();
      audit(request, ACTION_DRAFT_VCS_PULL,
              result.message() == null
                      ? StarterConstants.OUTCOME_SUCCESS
                      : StarterConstants.OUTCOME_FAILURE,
              pullDetails(result));
      publishEventBestEffort(new WorkspaceLifecycleEvent.DraftsRepoPulled(
              result.branch(), result.strategy(), result.pulled(), result.ahead(),
              result.behind(), null, correlationIdOf(request)));
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    } catch (RuntimeException e) {
      audit(request, ACTION_DRAFT_VCS_PULL, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  public ServerResponse rebase(ServerRequest request) {
    var configured = ensureConfigured();
    if (configured != null) {
      return configured;
    }
    var builder = builderClient();
    if (builder == null) {
      return gitRequiresBuilder();
    }
    try {
      RebaseResult result = builder.vcsRebase();
      audit(request, ACTION_DRAFT_VCS_REBASE,
              result.message() == null
                      ? StarterConstants.OUTCOME_SUCCESS
                      : StarterConstants.OUTCOME_FAILURE,
              rebaseDetails(result));
      publishEventBestEffort(new WorkspaceLifecycleEvent.DraftsRepoRebased(
              result.branch(), result.rebased(), result.ahead(), result.behind(), null,
              correlationIdOf(request)));
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    } catch (RuntimeException e) {
      audit(request, ACTION_DRAFT_VCS_REBASE, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  /**
   * Batch staging-area commit (invariant I2): every listed definition's source path is committed as
   * ONE atomic commit — all listed paths are committed or none, and unrelated staged files stay
   * staged. Pending writes are flushed first (I5); the single commit call is the same helper the
   * import-bundle flow uses.
   */
  public ServerResponse commit(ServerRequest request) throws IOException {
    var configured = ensureConfigured();
    if (configured != null) {
      return configured;
    }
    BatchCommitRequest body;
    try {
      body = objectMapper.readValue(request.body(String.class), BatchCommitRequest.class);
    } catch (JacksonException e) {
      log.warn("[DSL drafts] failed to parse batch commit body: {}", e.getMessage());
      return badRequest("malformed request body");
    } catch (ServletException e) {
      throw new IOException("Failed to read request body", e);
    }
    if (body == null || body.names() == null || body.names().isEmpty()) {
      return badRequest("names list is required");
    }
    if (body.message() == null || body.message().isBlank()) {
      return badRequest("message is required");
    }
    var builder = builderClient();
    if (builder == null) {
      return gitRequiresBuilder();
    }
    builder.flushFiles();
    PathResolution resolution = resolvePaths(body.names());
    if (resolution.missingName() != null) {
      return error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND",
                      "No source path for definition: " + resolution.missingName(), null, null,
                      null, null, null, null, null));
    }
    List<String> paths = resolution.paths();
    String actor = DslAuditService.currentActor();
    try {
      CommitResult result = builder.commit(new CommitRequest(paths, body.message(), actor,
              actor + "@cbs-nova.local"));
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("commitId", String.valueOf(result.commitId()));
      details.put("count", paths.size());
      details.put("paths", paths);
      if (result.pushed() != null) {
        details.put("pushed", result.pushed());
      }
      if (result.pushError() != null) {
        details.put("pushError", result.pushError());
      }
      audit(request, ACTION_DRAFTS_COMMIT, StarterConstants.OUTCOME_SUCCESS, details);
      publishEventBestEffort(new WorkspaceLifecycleEvent.DraftsCommitted(
              result.commitId(), paths.size(), body.message(), result.paths(), null,
              correlationIdOf(request)));
      log.info("[DSL drafts] batch-committed {} paths as {}", paths.size(), result.commitId());
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    } catch (RuntimeException e) {
      audit(request, ACTION_DRAFTS_COMMIT, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage()), "count", paths.size()));
      throw e;
    }
  }

  /**
   * Resolve every definition name to its source path. When at least one name has none, the result
   * carries it in {@code missingName} and no paths — the caller turns that into a 404 without
   * having committed anything (atomicity, I2).
   */
  private PathResolution resolvePaths(List<String> names) {
    var resolver = sourcePathResolverProvider == null
            ? null
            : sourcePathResolverProvider.getIfAvailable();
    List<String> paths = new java.util.ArrayList<>(names.size());
    for (String name : names) {
      Optional<String> path = resolver != null ? resolver.relativePath(name) : Optional.empty();
      if (path.isEmpty()) {
        log.warn("[DSL drafts] batch commit: no source path for definition {}", name);
        return new PathResolution(List.of(), name);
      }
      paths.add(path.get());
    }
    return new PathResolution(paths, null);
  }

  private record PathResolution(List<String> paths, @Nullable String missingName) {
  }

  private ServerResponse badRequest(String message) {
    return error(HttpStatus.BAD_REQUEST,
            new ErrorResponse("INVALID_REQUEST", message, null, null, null, null, null, null,
                    null));
  }

  private ServerResponse gitRequiresBuilder() {
    return error(HttpStatus.CONFLICT,
            new ErrorResponse(GIT_REQUIRES_BUILDER_CODE,
                    "Git operations require the DSL builder to be configured", null, null, null,
                    null, null, null, null));
  }

  private @Nullable DslBuilderClient builderClient() {
    if (builderClientProvider == null) {
      return null;
    }
    return builderClientProvider.getIfAvailable();
  }

  private void audit(ServerRequest request, String action, String outcome, Object details) {
    if (auditServiceProvider == null) {
      return;
    }
    var auditService = auditServiceProvider.getIfAvailable();
    if (auditService == null) {
      return;
    }
    auditService.record(DslAuditService.currentActor(), action, REPO_TARGET,
            DslAuditService.correlationIdOf(request), outcome, details);
  }

  private void publishEventBestEffort(@NonNull DomainEvent event) {
    var publisher = eventPublisherProvider == null
            ? null
            : eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    try {
      publisher.publish(event);
    } catch (RuntimeException e) {
      log.warn("[DSL events] best-effort publish of {} for aggregate {} failed: {}",
              event.eventType(), event.aggregateId(), e.getMessage());
    }
  }

  private @Nullable String correlationIdOf(@NonNull ServerRequest request) {
    try {
      return CorrelationId.validated(
              request.headers().firstHeader(StarterConstants.CORRELATION_ID_HEADER));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private ServerResponse ensureConfigured() {
    var sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return error(HttpStatus.CONFLICT,
              new ErrorResponse("NOT_CONFIGURED", "cbs.dsl.source-dir is not configured", null,
                      null, null, null, null, null, null));
    }
    if (!Files.isDirectory(Path.of(sourceDirProperty))) {
      return error(HttpStatus.CONFLICT,
              new ErrorResponse("NOT_FOUND",
                      "Source directory does not exist: " + sourceDirProperty,
                      null, null, null, null, null, null, null));
    }
    return null;
  }

  private static Map<String, Object> pullDetails(PullResult result) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("branch", String.valueOf(result.branch()));
    details.put("strategy", String.valueOf(result.strategy()));
    details.put("pulled", result.pulled());
    details.put("ahead", result.ahead());
    details.put("behind", result.behind());
    if (result.message() != null) {
      details.put("error", result.message());
    }
    return details;
  }

  private static Map<String, Object> rebaseDetails(RebaseResult result) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("branch", String.valueOf(result.branch()));
    details.put("rebased", result.rebased());
    details.put("ahead", result.ahead());
    details.put("behind", result.behind());
    if (result.message() != null) {
      details.put("error", result.message());
    }
    return details;
  }

  private static ServerResponse error(HttpStatus status, ErrorResponse body) {
    return ServerResponse.status(status).body(body);
  }
}
