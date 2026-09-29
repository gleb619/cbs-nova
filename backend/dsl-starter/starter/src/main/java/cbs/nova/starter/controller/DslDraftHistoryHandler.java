package cbs.nova.starter.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.model.LoadResult;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.utils.LineDiff;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.model.VcsModels.HistoryDiffResponse;
import cbs.nova.starter.model.VcsModels.LogEntry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Read-mostly history endpoints for DSL drafts. Extracted from {@link DslDraftHandler} per T731 —
 * same URLs, same status codes, same JSON shapes.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cbs.dsl.drafts", name = "enabled", havingValue = "true", matchIfMissing = true)
@AllArgsConstructor
public class DslDraftHistoryHandler {

  private final DslDraftSupport support;

  public ServerResponse history(ServerRequest request) {
    String name = request.pathVariable("name");
    var configured = support.ensureConfigured(name);
    if (configured.isError()) {
      return configured.response();
    }
    var sourcePath = support.resolveSourcePath(name);
    if (sourcePath.isEmpty()) {
      return support.error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "No source path for definition: " + name, name,
                      null, null, null, null, null, null));
    }
    var builder = support.builderClient();
    if (builder == null) {
      return support.gitRequiresBuilder();
    }
    String path = sourcePath.get();
    List<LogEntry> entries = builder.vcsLog(path, DslDraftSupport.HISTORY_LIMIT);
    List<DefinitionHistoryEntry> history = entries.stream()
            .map(e -> new DefinitionHistoryEntry(e.commitId(), e.timestampMillis(),
                    DslDraftSupport.HISTORY_SIZE_UNKNOWN, e.timestampMillis()))
            .toList();
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(history);
  }

  public ServerResponse historyEntry(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    String timestamp = request.pathVariable("timestamp");
    var configured = support.ensureConfigured(name);
    if (configured.isError()) {
      return configured.response();
    }
    var sourcePath = support.resolveSourcePath(name);
    if (sourcePath.isEmpty()) {
      return support.error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "No source path for definition: " + name, name,
                      null, null, null, null, null, null));
    }
    var builder = support.builderClient();
    if (builder == null) {
      return support.gitRequiresBuilder();
    }
    String path = sourcePath.get();
    try {
      String content = builder.vcsShow(path, timestamp);
      DraftRequest response = new DraftRequest(
              name,
              typeOf(name),
              "History",
              null,
              null,
              content,
              null);
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    } catch (BuilderApiException e) {
      if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return historyEntryNotFound(name, timestamp);
      }
      throw e;
    }
  }

  public ServerResponse historyDiff(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    String timestamp = request.pathVariable("timestamp");
    var configured = support.ensureConfigured(name);
    if (configured.isError()) {
      return configured.response();
    }
    var sourcePath = support.resolveSourcePath(name);
    if (sourcePath.isEmpty()) {
      return support.error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "No source path for definition: " + name, name,
                      null, null, null, null, null, null));
    }
    var builder = support.builderClient();
    if (builder == null) {
      return support.gitRequiresBuilder();
    }
    String path = sourcePath.get();
    try {
      String before = builder.vcsShow(path, timestamp);
      String after = support.readFile(path);
      LineDiff.Result result = LineDiff.diff(before, after, StarterConstants.DEFAULT_MAX_HUNKS,
              StarterConstants.LINE_DIFF_CONTEXT_LINES);
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
              .body(new HistoryDiffResponse(name, timestamp, before, after, result.hunks(),
                      result.truncated()));
    } catch (BuilderApiException e) {
      if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return historyEntryNotFound(name, timestamp);
      }
      throw e;
    }
  }

  public ServerResponse restore(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    String timestamp = request.pathVariable("timestamp");
    var configured = support.ensureConfigured(name);
    if (configured.isError()) {
      return configured.response();
    }
    var sourcePath = support.resolveSourcePath(name);
    if (sourcePath.isEmpty()) {
      return support.error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "No source path for definition: " + name, name,
                      null, null, null, null, null, null));
    }
    var builder = support.builderClient();
    if (builder == null) {
      return support.gitRequiresBuilder();
    }
    String path = sourcePath.get();
    try {
      String content = builder.vcsShow(path, timestamp);
      support.writeSource(path, content);
      DraftResponse response = new DraftResponse(name, "Draft", path, false,
              LoadResult.empty(), null, null, System.currentTimeMillis(), null);
      support.audit(request, DslDraftSupport.ACTION_DRAFT_RESTORE, name,
              StarterConstants.OUTCOME_SUCCESS,
              Map.of("location", path, "commitId", timestamp));
      log.info("[DSL drafts] restored {} (path={}) from commit {}", name, path, timestamp);
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    } catch (BuilderApiException e) {
      support.audit(request, DslDraftSupport.ACTION_DRAFT_RESTORE, name,
              StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return historyEntryNotFound(name, timestamp);
      }
      throw e;
    } catch (RuntimeException e) {
      support.audit(request, DslDraftSupport.ACTION_DRAFT_RESTORE, name,
              StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  public ServerResponse commits(ServerRequest request) {
    String name = request.pathVariable("name");
    var configured = support.ensureConfigured(name);
    if (configured.isError()) {
      return configured.response();
    }
    var sourcePath = support.resolveSourcePath(name);
    if (sourcePath.isEmpty()) {
      return support.error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "No source path for definition: " + name, name,
                      null, null, null, null, null, null));
    }
    var builder = support.builderClient();
    if (builder == null) {
      return support.gitRequiresBuilder();
    }
    int limit = parseLimit(request);
    String path = sourcePath.get();
    List<LogEntry> entries = builder.vcsLog(path, limit);
    log.info("[DSL drafts] listed {} commits for {} (path={})", entries.size(), name, path);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(entries);
  }

  private ServerResponse historyEntryNotFound(String name, String timestamp) {
    return support.error(HttpStatus.NOT_FOUND,
            new ErrorResponse("NOT_FOUND",
                    "No publish history entry " + timestamp + " for " + name, name, null, null,
                    null, null, null, null));
  }

  private int parseLimit(ServerRequest request) {
    try {
      return request.param("limit")
              .map(Integer::parseInt)
              .map(v -> Math.max(1, Math.min(v, DslDraftSupport.MAX_LOG_LIMIT)))
              .orElse(Integer.parseInt(DslDraftSupport.DEFAULT_LOG_LIMIT));
    } catch (NumberFormatException e) {
      return Integer.parseInt(DslDraftSupport.DEFAULT_LOG_LIMIT);
    }
  }

  private String typeOf(String name) {
    GlobalManager gm = GlobalManager.globalManager();
    if (gm.findProcess(name) != null) {
      return "process";
    }
    if (gm.findTransaction(name) != null) {
      return "transaction";
    }
    if (gm.findFunction(name) != null) {
      return "function";
    }
    return null;
  }
}
