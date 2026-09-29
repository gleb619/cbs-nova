package cbs.nova.starter.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.service.CorrelationId;
import cbs.nova.starter.service.DomainEventPublisher;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslSourcePathResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Package-private collaborator for {@link DslDraftHandler} and {@link DslDraftHistoryHandler}.
 * Holds the shared private helpers (resolver access, builder client lookup, builder-required git
 * error, audit/event plumbing, response shaping) that both draft handlers rely on. Pure extraction
 * — no behaviour change.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cbs.dsl.drafts", name = "enabled", havingValue = "true", matchIfMissing = true)
@AllArgsConstructor
public class DslDraftSupport {

  static final String GIT_NOT_CONFIGURED_CODE = "GIT_NOT_CONFIGURED";
  static final String GIT_REQUIRES_BUILDER_CODE = "GIT_REQUIRES_BUILDER";
  /**
   * Maximum entries returned by the history endpoint.
   */
  public static final int HISTORY_LIMIT = 50;
  static final long HISTORY_SIZE_UNKNOWN = -1L;
  static final String DEFAULT_LOG_LIMIT = "20";
  static final int MAX_LOG_LIMIT = 200;
  static final String ACTION_DRAFT_RESTORE = "DRAFT_RESTORE";

  private final DslProperties dslProperties;
  private final ObjectProvider<DslBuilderClient> builderClientProvider;
  private final ObjectProvider<DslAuditService> auditServiceProvider;
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;
  private final ObjectProvider<DslSourcePathResolver> sourcePathResolverProvider;

  PathResult ensureConfigured(String name) {
    var sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return new PathResult.Err(error(HttpStatus.CONFLICT,
              new ErrorResponse("NOT_CONFIGURED", "cbs.dsl.source-dir is not configured", name,
                      null, null, null, null, null, null)));
    }
    Path dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      return new PathResult.Err(error(HttpStatus.CONFLICT,
              new ErrorResponse("NOT_FOUND", "Source directory does not exist: " + dir, name, null,
                      null, null, null, null, null)));
    }
    return new PathResult.Ok(dir);
  }

  ServerResponse gitRequiresBuilder() {
    return error(HttpStatus.CONFLICT,
            new ErrorResponse(GIT_REQUIRES_BUILDER_CODE,
                    "Git operations require the DSL builder to be configured", null, null, null,
                    null, null, null, null));
  }

  Optional<String> resolveSourcePath(String name) {
    if (sourcePathResolverProvider == null) {
      return Optional.empty();
    }
    var resolver = sourcePathResolverProvider.getIfAvailable();
    if (resolver == null) {
      return Optional.empty();
    }
    return resolver.relativePath(name);
  }

  @Nullable
  DslBuilderClient builderClient() {
    if (builderClientProvider == null) {
      return null;
    }
    return builderClientProvider.getIfAvailable();
  }

  DslBuilderClient requireBuilderClient() {
    var builder = builderClient();
    if (builder == null) {
      throw new IllegalStateException("DslBuilderClient is not available");
    }
    return builder;
  }

  void writeSource(String relativePath, String content) {
    var builder = requireBuilderClient();
    builder.stageWrite(relativePath, content);
    builder.flushFiles();
  }

  void flushFiles() {
    requireBuilderClient().flushFiles();
  }

  String readFile(String relativePath) {
    return requireBuilderClient().readFile(relativePath).content();
  }

  void audit(ServerRequest request, String action, String target, String outcome,
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

  void publishEventBestEffort(@NonNull DomainEvent event) {
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

  @Nullable
  String correlationIdOf(@NonNull ServerRequest request) {
    try {
      return CorrelationId.validated(
              request.headers().firstHeader(StarterConstants.CORRELATION_ID_HEADER));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  static ServerResponse error(HttpStatus status, ErrorResponse body) {
    return ServerResponse.status(status).body(body);
  }

  sealed interface PathResult {

    Path path();

    default boolean isError() {
      return false;
    }

    default ServerResponse response() {
      throw new IllegalStateException("not an error result");
    }

    record Ok(Path path) implements PathResult {
    }

    record Err(ServerResponse response) implements PathResult {

      @Override
      public Path path() {
        throw new IllegalStateException("not a path");
      }

      @Override
      public boolean isError() {
        return true;
      }
    }
  }
}
