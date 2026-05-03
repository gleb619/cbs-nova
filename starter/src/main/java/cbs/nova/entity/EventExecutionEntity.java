package cbs.nova.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "event_execution")
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class EventExecutionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_code", nullable = false, length = 100)
  private String eventCode;

  @Column(name = "dsl_version", nullable = false, length = 50)
  private String dslVersion;

  @Column(nullable = false, length = 20)
  private String action;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EventStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String context;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "executed_transactions", nullable = false, columnDefinition = "jsonb")
  private String executedTransactions;

  @Column(name = "temporal_workflow_id", length = 200)
  private String temporalWorkflowId;

  @Column(name = "workflow_execution_id")
  private Long workflowExecutionId;

  @Column(name = "performed_by", nullable = false, length = 200)
  private String performedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;
}
