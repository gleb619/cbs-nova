package cbs.nova.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mass_operation_execution")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MassOperationExecutionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String code;

  @Column(nullable = false)
  private String category;

  @Column(name = "dsl_version", nullable = false)
  private String dslVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MassOperationStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String context;

  @Column(name = "total_items", nullable = false)
  private Long totalItems;

  @Column(name = "processed_count", nullable = false)
  private Long processedCount;

  @Column(name = "failed_count", nullable = false)
  private Long failedCount;

  @Column(name = "trigger_type", nullable = false)
  private String triggerType;

  @Column(name = "trigger_source")
  private String triggerSource;

  @Column(name = "performed_by", nullable = false)
  private String performedBy;

  @Column(name = "started_at", nullable = false, updatable = false)
  private OffsetDateTime startedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "temporal_workflow_id")
  private String temporalWorkflowId;
}
