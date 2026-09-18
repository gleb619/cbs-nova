package cbs.nova.starter.events;

import cbs.nova.dsl.history.DslRunStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public sealed interface DomainEvent permits
        DomainEvent.RunLifecycleEvent,
        DomainEvent.DefinitionLifecycleEvent,
        DomainEvent.PieceNotified {

  @JsonProperty("eventType")
  String eventType();

  @JsonProperty("aggregateType")
  String aggregateType();

  @JsonProperty("aggregateId")
  String aggregateId();

  @JsonProperty("correlationId")
  @Nullable
  String correlationId();

  @JsonProperty("schemaVersion")
  default int schemaVersion() {
    return 1;
  }

  @JsonProperty("occurredAt")
  @Nullable
  Instant occurredAt();

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
  @JsonSubTypes({
      @JsonSubTypes.Type(value = RunStarted.class, name = "RunStarted"),
      @JsonSubTypes.Type(value = RunCompleted.class, name = "RunCompleted"),
      @JsonSubTypes.Type(value = RunFailed.class, name = "RunFailed"),
      @JsonSubTypes.Type(value = RunCancelled.class, name = "RunCancelled"),
      @JsonSubTypes.Type(value = RunStale.class, name = "RunStale"),
      @JsonSubTypes.Type(value = DraftSaved.class, name = "DraftSaved"),
      @JsonSubTypes.Type(value = DraftPublished.class, name = "DraftPublished"),
      @JsonSubTypes.Type(value = ReloadFailed.class, name = "ReloadFailed"),
      @JsonSubTypes.Type(value = PieceNotified.class, name = "PieceNotified"),
  })
  sealed interface RunLifecycleEvent extends DomainEvent permits
          RunStarted, RunCompleted, RunFailed, RunCancelled, RunStale {

    @Override
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @NonNull
    String aggregateId();

    @Override
    @JsonProperty("aggregateType")
    default String aggregateType() {
      return "run";
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
  @JsonSubTypes({
      @JsonSubTypes.Type(value = DraftSaved.class, name = "DraftSaved"),
      @JsonSubTypes.Type(value = DraftPublished.class, name = "DraftPublished"),
      @JsonSubTypes.Type(value = ReloadFailed.class, name = "ReloadFailed"),
  })
  sealed interface DefinitionLifecycleEvent extends DomainEvent permits
          DraftSaved, DraftPublished, ReloadFailed {

    @Override
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @NonNull
    String aggregateId();

    @Override
    @JsonProperty("aggregateType")
    default String aggregateType() {
      return "definition";
    }
  }

  record RunStarted(
          @JsonProperty("runId") @NonNull String runId,
          @JsonProperty("processName") @NonNull String processName,
          @JsonProperty("triggeredBy") @Nullable String triggeredBy,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            RunLifecycleEvent {

    public RunStarted {
      if (runId.isBlank()) {
        throw new IllegalArgumentException("runId must not be blank");
      }
    }

    @Override
    public String eventType() {
      return "RunStarted";
    }

    @Override
    public String aggregateId() {
      return runId;
    }
  }

  record RunCompleted(
          @JsonProperty("runId") @NonNull String runId,
          @JsonProperty("processName") @NonNull String processName,
          @JsonProperty("status") @NonNull DslRunStatus status,
          @JsonProperty("outputJson") @Nullable String outputJson,
          @JsonProperty("error") @Nullable String error,
          @JsonProperty("startedAt") @NonNull Instant startedAt,
          @JsonProperty("finishedAt") @NonNull Instant finishedAt,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            RunLifecycleEvent {

    public RunCompleted {
      if (runId.isBlank()) {
        throw new IllegalArgumentException("runId must not be blank");
      }
      if (status != DslRunStatus.COMPLETED) {
        throw new IllegalArgumentException(
                "RunCompleted requires status=COMPLETED, was " + status);
      }
    }

    @Override
    public String eventType() {
      return "RunCompleted";
    }

    @Override
    public String aggregateId() {
      return runId;
    }
  }

  record RunFailed(
          @JsonProperty("runId") @NonNull String runId,
          @JsonProperty("processName") @NonNull String processName,
          @JsonProperty("status") @NonNull DslRunStatus status,
          @JsonProperty("error") @Nullable String error,
          @JsonProperty("startedAt") @NonNull Instant startedAt,
          @JsonProperty("finishedAt") @NonNull Instant finishedAt,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            RunLifecycleEvent {

    public RunFailed {
      if (runId.isBlank()) {
        throw new IllegalArgumentException("runId must not be blank");
      }
      if (status != DslRunStatus.FAILED) {
        throw new IllegalArgumentException(
                "RunFailed requires status=FAILED, was " + status);
      }
    }

    @Override
    public String eventType() {
      return "RunFailed";
    }

    @Override
    public String aggregateId() {
      return runId;
    }
  }

  record RunCancelled(
          @JsonProperty("runId") @NonNull String runId,
          @JsonProperty("processName") @NonNull String processName,
          @JsonProperty("status") @NonNull DslRunStatus status,
          @JsonProperty("error") @Nullable String error,
          @JsonProperty("startedAt") @NonNull Instant startedAt,
          @JsonProperty("finishedAt") @NonNull Instant finishedAt,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            RunLifecycleEvent {

    public RunCancelled {
      if (runId.isBlank()) {
        throw new IllegalArgumentException("runId must not be blank");
      }
      if (status != DslRunStatus.CANCELLED) {
        throw new IllegalArgumentException(
                "RunCancelled requires status=CANCELLED, was " + status);
      }
    }

    @Override
    public String eventType() {
      return "RunCancelled";
    }

    @Override
    public String aggregateId() {
      return runId;
    }
  }

  record RunStale(
          @JsonProperty("runId") @NonNull String runId,
          @JsonProperty("processName") @NonNull String processName,
          @JsonProperty("status") @NonNull DslRunStatus status,
          @JsonProperty("error") @Nullable String error,
          @JsonProperty("startedAt") @NonNull Instant startedAt,
          @JsonProperty("finishedAt") @NonNull Instant finishedAt,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            RunLifecycleEvent {

    public RunStale {
      if (runId.isBlank()) {
        throw new IllegalArgumentException("runId must not be blank");
      }
      if (status != DslRunStatus.STALE) {
        throw new IllegalArgumentException(
                "RunStale requires status=STALE, was " + status);
      }
    }

    @Override
    public String eventType() {
      return "RunStale";
    }

    @Override
    public String aggregateId() {
      return runId;
    }
  }

  record DraftSaved(
          @JsonProperty("definitionName") @NonNull String definitionName,
          @JsonProperty("version") @Nullable String version,
          @JsonProperty("taskQueue") @Nullable String taskQueue,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            DefinitionLifecycleEvent {

    public DraftSaved {
      if (definitionName.isBlank()) {
        throw new IllegalArgumentException("definitionName must not be blank");
      }
    }

    @Override
    public String eventType() {
      return "DraftSaved";
    }

    @Override
    public String aggregateId() {
      return definitionName;
    }
  }

  record DraftPublished(
          @JsonProperty("definitionName") @NonNull String definitionName,
          @JsonProperty("version") @Nullable String version,
          @JsonProperty("taskQueue") @Nullable String taskQueue,
          @JsonProperty("reloaded") boolean reloaded,
          @JsonProperty("location") @Nullable String location,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            DefinitionLifecycleEvent {

    public DraftPublished {
      if (definitionName.isBlank()) {
        throw new IllegalArgumentException("definitionName must not be blank");
      }
    }

    @Override
    public String eventType() {
      return "DraftPublished";
    }

    @Override
    public String aggregateId() {
      return definitionName;
    }
  }

  record ReloadFailed(
          @JsonProperty("definitionName") @Nullable String definitionName,
          @JsonProperty("source") @Nullable String source,
          @JsonProperty("error") @NonNull String error,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            DefinitionLifecycleEvent {

    public ReloadFailed {
      if (error.isBlank()) {
        throw new IllegalArgumentException("error must not be blank");
      }
    }

    @Override
    public String eventType() {
      return "ReloadFailed";
    }

    @Override
    public String aggregateType() {
      return "definition";
    }

    @Override
    public String aggregateId() {
      return definitionName != null ? definitionName : source != null ? source : "-";
    }
  }

  /**
   * Best-effort notification emitted by the {@code postCheck: notify} hook (T550). Log/event only —
   * real notification sinks are Epic 3.
   */
  record PieceNotified(
          @JsonProperty("pieceId") @NonNull String pieceId,
          @JsonProperty("channel") @NonNull String channel,
          @JsonProperty("occurredAt") @Nullable Instant occurredAt,
          @JsonProperty("correlationId") @Nullable String correlationId)
          implements
            DomainEvent {

    public PieceNotified {
      if (pieceId.isBlank()) {
        throw new IllegalArgumentException("pieceId must not be blank");
      }
      if (channel.isBlank()) {
        throw new IllegalArgumentException("channel must not be blank");
      }
    }

    @Override
    public String eventType() {
      return "PieceNotified";
    }

    @Override
    public String aggregateType() {
      return "piece";
    }

    @Override
    public String aggregateId() {
      return pieceId;
    }
  }
}
