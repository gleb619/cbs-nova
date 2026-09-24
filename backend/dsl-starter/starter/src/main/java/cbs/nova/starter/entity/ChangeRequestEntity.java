package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Row of {@code dsl_change_request} (T568): a snapshot of a Workbench draft payload awaiting
 * approval before publish. {@code draftContent} holds the raw draft JSON exactly as stored under
 * {@code .workbench/drafts}; on approval the snapshot (not the current draft file) is published.
 */
@Table("dsl_change_request")
public record ChangeRequestEntity(
        @Id @Column("id") @Nullable Long id,
        @Column("definition_name") String definitionName,
        @Column("draft_content") String draftContent,
        @Column("requested_by") String requestedBy,
        @Column("requested_at") Instant requestedAt,
        @Column("status") Status status,
        @Column("approved_by") @Nullable String approvedBy,
        @Column("approved_at") @Nullable Instant approvedAt,
        @Column("comment") @Nullable String comment) {

  public enum Status {
    PENDING, APPROVED, REJECTED, SUPERSEDED
  }
}
