package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Row of {@code dsl_change_request} (T568): a snapshot of a Workbench draft payload awaiting
 * approval before publish. {@code draftContent} holds the raw draft JSON exactly as stored under
 * {@code .workbench/drafts}; on approval the snapshot (not the current draft file) is published.
 */
public record ChangeRequestEntity(
        @Nullable Long id,
        String definitionName,
        String draftContent,
        String requestedBy,
        Instant requestedAt,
        Status status,
        @Nullable String approvedBy,
        @Nullable Instant approvedAt,
        @Nullable String comment) {

  /** Lifecycle of a change request. Only {@link #PENDING} rows can be approved or rejected. */
  public enum Status {
    PENDING, APPROVED, REJECTED, SUPERSEDED
  }
}
