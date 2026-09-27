package cbs.nova.starter.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Workspace-level git operations on the drafts repository (fetch/pull/rebase/batch commit), as
 * opposed to the per-definition lifecycle in {@link DomainEvent.DefinitionLifecycleEvent}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = WorkspaceLifecycleEvent.DraftsRepoFetched.class, name = "DraftsRepoFetched"),
    @JsonSubTypes.Type(value = WorkspaceLifecycleEvent.DraftsRepoPulled.class, name = "DraftsRepoPulled"),
    @JsonSubTypes.Type(value = WorkspaceLifecycleEvent.DraftsRepoRebased.class, name = "DraftsRepoRebased"),
    @JsonSubTypes.Type(value = WorkspaceLifecycleEvent.DraftsCommitted.class, name = "DraftsCommitted"),
})
public sealed interface WorkspaceLifecycleEvent extends DomainEvent
        permits WorkspaceLifecycleEvent.DraftsRepoFetched, WorkspaceLifecycleEvent.DraftsRepoPulled,
        WorkspaceLifecycleEvent.DraftsRepoRebased, WorkspaceLifecycleEvent.DraftsCommitted {

  @Override
  @JsonProperty("aggregateType")
  default String aggregateType() {
    return "workspace";
  }

  record DraftsRepoFetched(
          @JsonProperty("branch") @NonNull String branch,
          @JsonProperty("fetched") boolean fetched,
          @JsonProperty("ahead") int ahead,
          @JsonProperty("behind") int behind,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            WorkspaceLifecycleEvent {

    public DraftsRepoFetched {
      if (branch.isBlank()) {
        throw new IllegalArgumentException("branch must not be blank");
      }
    }

    @Override
    public String eventType() {
      return "DraftsRepoFetched";
    }

    @Override
    public String aggregateId() {
      return branch;
    }
  }

  record DraftsRepoPulled(
          @JsonProperty("branch") @NonNull String branch,
          @JsonProperty("strategy") @NonNull String strategy,
          @JsonProperty("pulled") boolean pulled,
          @JsonProperty("ahead") int ahead,
          @JsonProperty("behind") int behind,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            WorkspaceLifecycleEvent {

    public DraftsRepoPulled {
      if (branch.isBlank()) {
        throw new IllegalArgumentException("branch must not be blank");
      }
    }

    @Override
    public String eventType() {
      return "DraftsRepoPulled";
    }

    @Override
    public String aggregateId() {
      return branch;
    }
  }

  record DraftsRepoRebased(
          @JsonProperty("branch") @NonNull String branch,
          @JsonProperty("rebased") boolean rebased,
          @JsonProperty("ahead") int ahead,
          @JsonProperty("behind") int behind,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            WorkspaceLifecycleEvent {

    public DraftsRepoRebased {
      if (branch.isBlank()) {
        throw new IllegalArgumentException("branch must not be blank");
      }
    }

    @Override
    public String eventType() {
      return "DraftsRepoRebased";
    }

    @Override
    public String aggregateId() {
      return branch;
    }
  }

  record DraftsCommitted(
          @JsonProperty("commitId") @NonNull String commitId,
          @JsonProperty("count") int count,
          @JsonProperty("message") @NonNull String message,
          @JsonProperty("paths") @Nullable List<String> paths,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            WorkspaceLifecycleEvent {

    public DraftsCommitted {
      if (commitId.isBlank()) {
        throw new IllegalArgumentException("commitId must not be blank");
      }
      if (message.isBlank()) {
        throw new IllegalArgumentException("message must not be blank");
      }
    }

    @Override
    public String eventType() {
      return "DraftsCommitted";
    }

    @Override
    public String aggregateId() {
      return commitId;
    }
  }
}
