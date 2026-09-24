package cbs.nova.starter.controller;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.PromotionModels.PromotionDefinition;
import cbs.nova.starter.model.PromotionModels.PromotionEnvironment;
import cbs.nova.starter.model.PromotionModels.PromotionRequest;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.starter.model.VcsModels.ImportBundleResult;
import cbs.nova.starter.model.VcsModels.ImportEntryResult;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslDefinitionBundleService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Functional handler for the T569 environment-promotion workflow. Promotion exports a bundle from
 * one configured environment workbench directory, previews the diff against the target via the
 * existing {@link DslDefinitionBundleService#diffForImport} machinery, and — on apply — writes the
 * DSL source files to the target and proves the result with digest verification.
 *
 * <p>
 * The target environment is NOT reloaded: it is a separate deployment that picks up its files on
 * its own reload cycle. Every apply is recorded in the audit log with actor, source, target and
 * definition names.
 */
@Slf4j
@Component
@AllArgsConstructor
public class DslPromoteHandler {

  static final String ACTION_PROMOTION = "PROMOTION";

  private final DslProperties dslProperties;
  private final DslDefinitionBundleService bundleService;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<DslAuditService> auditServiceProvider;

  public ServerResponse environments(ServerRequest request) {
    List<PromotionEnvironment> envs = dslProperties.promotion().environments().keySet().stream()
            .sorted()
            .map(PromotionEnvironment::new)
            .toList();
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(envs);
  }

  public ServerResponse definitions(ServerRequest request) {
    String envName = request.param("env").orElse("");
    PathResult dir = resolveEnvironment(envName, "env");
    if (dir.isError()) {
      return dir.response();
    }
    List<PromotionDefinition> defs = bundleService.export(dir.path(), false).definitions().stream()
            .map(e -> new PromotionDefinition(e.definition().name(), e.definition().type(),
                    e.definition().status()))
            .sorted(Comparator.comparing(PromotionDefinition::name))
            .toList();
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(defs);
  }

  public ServerResponse promote(ServerRequest request) throws IOException {
    boolean dryRun = request.param("dryRun").map(Boolean::parseBoolean).orElse(false);

    PromotionRequest body;
    try {
      body = objectMapper.readValue(request.body(String.class), PromotionRequest.class);
    } catch (Exception e) {
      log.warn("[DSL promote] failed to parse request body: {}", e.getMessage());
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "malformed promotion request JSON", null, null,
                      null, null, null, null, null));
    }
    if (body == null || body.source() == null || body.source().isBlank()
            || body.target() == null || body.target().isBlank()) {
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "source and target are required", null, null,
                      null, null, null, null, null));
    }
    if (body.source().equals(body.target())) {
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "source and target must differ", null, null,
                      null, null, null, null, null));
    }

    PathResult source = resolveEnvironment(body.source(), "source");
    if (source.isError()) {
      return source.response();
    }
    PathResult target = resolveEnvironment(body.target(), "target");
    if (target.isError()) {
      return target.response();
    }

    DefinitionBundle bundle = bundleService.exportSelected(source.path(),
            Boolean.TRUE.equals(body.includeDrafts()), body.definitions());
    try {
      bundleService.validateForImport(bundle);
      bundleService.verifyDigest(bundle);
    } catch (IllegalArgumentException e) {
      log.warn("[DSL promote] bundle validation failed: {}", e.getMessage());
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("BAD_REQUEST", e.getMessage(), null, null, null, null, null, null,
                      null));
    }

    if (dryRun) {
      List<ImportEntryResult> results = bundleService.diffForImport(target.path(), bundle);
      int wouldChange = (int) results.stream()
              .filter(r -> "created".equals(r.outcome()) || "updated".equals(r.outcome()))
              .count();
      int skipped = (int) results.stream().filter(r -> "skipped".equals(r.outcome())).count();
      log.info("[DSL promote] dry-run {} -> {}: {} created/updated, {} skipped, digest={}",
              body.source(), body.target(), wouldChange, skipped, bundle.digest());
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
              .body(new ImportBundleResult(true, false, wouldChange, skipped, results, null, null));
    }

    List<ImportEntryResult> results = new ArrayList<>();
    try {
      results.addAll(bundleService.applyToTarget(target.path(), bundle));
      bundleService.verifyApplied(target.path(), bundle);
    } catch (RuntimeException e) {
      audit(request, body, bundle, StarterConstants.OUTCOME_FAILURE, e.getMessage());
      throw e;
    }
    long published = results.stream().filter(r -> "published".equals(r.outcome())).count();
    long failed = results.size() - published;
    audit(request, body, bundle, StarterConstants.OUTCOME_SUCCESS, null);
    log.info("[DSL promote] applied {} -> {}: {} published, {} failed, digest verified={}",
            body.source(), body.target(), published, failed, bundle.digest());
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
            .body(new ImportBundleResult(false, false, (int) published, (int) failed, results,
                    null, null));
  }

  private void audit(ServerRequest request, PromotionRequest body, DefinitionBundle bundle,
          String outcome, String error) {
    var provider = auditServiceProvider == null ? null : auditServiceProvider.getIfAvailable();
    if (provider == null) {
      return;
    }
    List<String> names = bundle.definitions().stream()
            .map(e -> e.definition().name())
            .toList();
    Object details = error != null
            ? Map.of("source", body.source(), "target", body.target(), "error", error)
            : Map.of("source", body.source(), "target", body.target(), "definitions", names,
                    "digest", Optional.ofNullable(bundle.digest()).orElse(""), "count",
                    names.size());
    provider.record(DslAuditService.currentActor(), ACTION_PROMOTION, body.target(),
            DslAuditService.correlationIdOf(request), outcome, details);
  }

  private PathResult resolveEnvironment(String name, String param) {
    var env = dslProperties.promotion().environments().get(name);
    if (env == null) {
      return new PathResult.Err(error(HttpStatus.NOT_FOUND,
              new ErrorResponse("ENV_NOT_FOUND", "environment not configured: " + name, param, null,
                      null, null, null, null, null)));
    }
    Path dir = Path.of(env.basePath());
    if (!dir.isAbsolute()) {
      String sourceDir = dslProperties.sourceDir();
      if (sourceDir != null && !sourceDir.isBlank()) {
        dir = Path.of(sourceDir).resolve(dir);
      }
    }
    if (!Files.isDirectory(dir)) {
      return new PathResult.Err(error(HttpStatus.NOT_FOUND,
              new ErrorResponse("ENV_NOT_FOUND", "environment directory does not exist: " + dir,
                      param, null, null, null, null, null, null)));
    }
    return new PathResult.Ok(dir);
  }

  private sealed interface PathResult {

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

  private static ServerResponse error(HttpStatus status, ErrorResponse body) {
    return ServerResponse.status(status).body(body);
  }

}
