package cbs.nova.starter.service;

import static cbs.nova.starter.core.StarterConstants.OUTCOME_FAILURE;
import static cbs.nova.starter.core.StarterConstants.OUTCOME_SUCCESS;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.entity.ChangeRequestEntity;
import cbs.nova.starter.entity.ChangeRequestEntity.Status;
import cbs.nova.starter.exception.ChangeRequestException;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.persistence.ChangeRequestRepository;
import cbs.nova.starter.security.Role;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Change-request lifecycle (T568): submit snapshots the current draft file, approve validates the
 * approver (AUTHOR rank, no self-approval below ADMIN) and delegates the actual publish to
 * {@link DslDraftHandler#publishPayload} so the publish flow is not duplicated. Every outcome is
 * audited through {@link DslAuditService} (fail-safe by design).
 *
 * <p>
 * There is no surrounding DB transaction: the draft filesystem write is the state change itself
 * (same convention as {@code DslDraftHandler}), so the status update and the publish are
 * best-effort sequential steps.
 */
@Slf4j
@RequiredArgsConstructor
public class ChangeRequestService {

  private final ChangeRequestRepository repository;
  private final DslProperties dslProperties;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<DslDraftHandler> draftHandlerProvider;
  private final ObjectProvider<DslAuditService> auditService;

  /**
   * Snapshots the current draft of {@code definitionName} into a new PENDING change request,
   * superseding any previous PENDING request for the same definition.
   *
   * @throws ChangeRequestException
   *           {@code NOT_FOUND} when no draft file exists, {@code CONFLICT} when the drafts
   *           directory is not configured.
   */
  public ChangeRequestEntity submit(String definitionName, String actor, ServerRequest request) {
    try {
      String content = readDraftSnapshot(definitionName);
      repository.findPendingByDefinitionName(definitionName).ifPresent(pending -> repository
              .updateStatus(pending.id(), Status.SUPERSEDED, null, null, null));
      ChangeRequestEntity row = new ChangeRequestEntity(null, definitionName, content, actor,
              Instant.now(), Status.PENDING, null, null, null);
      long id = repository.insert(row);
      ChangeRequestEntity created = new ChangeRequestEntity(id, row.definitionName(),
              row.draftContent(), row.requestedBy(), row.requestedAt(), row.status(),
              row.approvedBy(), row.approvedAt(), row.comment());
      audit(request, StarterConstants.ACTION_CHANGE_REQUEST_CREATE, id, actor, OUTCOME_SUCCESS,
              Map.of("definitionName", definitionName));
      return created;
    } catch (ChangeRequestException e) {
      audit(request, StarterConstants.ACTION_CHANGE_REQUEST_CREATE, null, actor, OUTCOME_FAILURE,
              Map.of("definitionName", definitionName, "error", e.getMessage()));
      throw e;
    }
  }

  /**
   * Approves a PENDING change request and publishes its snapshot through
   * {@link DslDraftHandler#publishPayload}. The caller's role must satisfy {@link Role#AUTHOR} and
   * the approver must not be the requester unless the role is {@link Role#ADMIN}.
   *
   * @return the publish response produced by the draft handler.
   * @throws ChangeRequestException
   *           {@code NOT_FOUND} / {@code CONFLICT} (not pending) / {@code FORBIDDEN} (rank or
   *           self-approval).
   */
  public ServerResponse approve(long id, String actor, Role role, @Nullable String comment,
          ServerRequest request) throws IOException {
    ChangeRequestEntity row = requirePending(id, actor, role, request,
            StarterConstants.ACTION_CHANGE_REQUEST_APPROVE);
    repository.updateStatus(id, Status.APPROVED, actor, Instant.now(), comment);
    audit(request, StarterConstants.ACTION_CHANGE_REQUEST_APPROVE, id, actor, OUTCOME_SUCCESS,
            Map.of("definitionName", row.definitionName()));
    DslDraftHandler draftHandler = draftHandlerProvider.getIfAvailable();
    if (draftHandler == null) {
      throw new ChangeRequestException("CONFLICT", "Draft publishing is not available");
    }
    DraftRequest snapshot = objectMapper.readValue(row.draftContent(), DraftRequest.class);
    DraftRequest payload = new DraftRequest(snapshot.name(), snapshot.type(), "Published",
            snapshot.version(), snapshot.taskQueue(), snapshot.source(), snapshot.savedAt());
    return draftHandler.publishPayload(request, row.definitionName(), payload);
  }

  /**
   * Rejects a PENDING change request. Same rank and self-approval guards as {@link #approve}; never
   * publishes.
   */
  public ChangeRequestEntity reject(long id, String actor, Role role, @Nullable String comment,
          ServerRequest request) {
    ChangeRequestEntity row = requirePending(id, actor, role, request,
            StarterConstants.ACTION_CHANGE_REQUEST_REJECT);
    repository.updateStatus(id, Status.REJECTED, null, null, comment);
    audit(request, StarterConstants.ACTION_CHANGE_REQUEST_REJECT, id, actor, OUTCOME_SUCCESS,
            Map.of("definitionName", row.definitionName()));
    return new ChangeRequestEntity(row.id(), row.definitionName(), row.draftContent(),
            row.requestedBy(), row.requestedAt(), Status.REJECTED, null, null, comment);
  }

  public List<ChangeRequestEntity> list(@Nullable String definitionName, @Nullable Status status) {
    return repository.findAll(definitionName, status);
  }

  public ChangeRequestEntity findById(long id) {
    return repository.findById(id)
            .orElseThrow(() -> new ChangeRequestException("NOT_FOUND", "No change request: " + id));
  }

  private ChangeRequestEntity requirePending(long id, String actor, Role role,
          ServerRequest request,
          String action) {
    ChangeRequestException failure;
    ChangeRequestEntity row = repository.findById(id).orElse(null);
    if (row == null) {
      failure = new ChangeRequestException("NOT_FOUND", "No change request: " + id);
    } else if (row.status() != Status.PENDING) {
      failure = new ChangeRequestException("CONFLICT",
              "Change request " + id + " is " + row.status() + " and can no longer be decided");
    } else if (!role.satisfies(Role.AUTHOR)) {
      failure = new ChangeRequestException("FORBIDDEN",
              "Role " + Role.AUTHOR + " is required to decide change requests; caller has role "
                      + role);
    } else if (actor.equals(row.requestedBy()) && role != Role.ADMIN) {
      failure = new ChangeRequestException("FORBIDDEN",
              "Change request requester cannot approve or reject their own request");
    } else {
      return row;
    }
    audit(request, action, id, actor, OUTCOME_FAILURE, Map.of("error", failure.getMessage()));
    throw failure;
  }

  private String readDraftSnapshot(String definitionName) {
    var sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      throw new ChangeRequestException("CONFLICT", "csb.dsl.source-dir is not configured");
    }
    Path draftsDir = Path.of(sourceDirProperty).resolve(StarterConstants.WORKBENCH_DRAFTS_DIR);
    Path draftFile = draftsDir.resolve(safeFileName(definitionName) + ".json").normalize();
    if (!draftFile.startsWith(draftsDir) || !Files.exists(draftFile)) {
      throw new ChangeRequestException("NOT_FOUND", "No draft found for: " + definitionName);
    }
    try {
      return Files.readString(draftFile, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ChangeRequestException("CONFLICT",
              "Failed to read draft for " + definitionName + ": " + e.getMessage());
    }
  }

  private static String safeFileName(String name) {
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private void audit(@Nullable ServerRequest request, String action, @Nullable Long id,
          String actor,
          String outcome, Object details) {
    try {
      DslAuditService audit = auditService.getIfAvailable();
      if (audit != null) {
        String correlationId = request == null ? null : DslAuditService.correlationIdOf(request);
        audit.record(actor, action, id != null ? "change-request:" + id : "change-request",
                correlationId, outcome, details);
      }
    } catch (Exception ex) {
      log.warn("[DSL change-requests] failed to audit {} on change request {}: {}", action, id,
              ex.getMessage());
    }
  }
}
