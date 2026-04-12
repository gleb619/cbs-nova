package cbs.nova.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mass_operation_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MassOperationItemEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "mass_operation_execution_id", nullable = false)
  private MassOperationExecutionEntity massOperationExecution;

  @Column(name = "item_key", nullable = false)
  private String itemKey;

  @Column(name = "item_data", nullable = false, columnDefinition = "jsonb")
  private String itemData;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MassOperationItemStatus status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_execution_id")
  private WorkflowExecutionEntity workflowExecution;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "retry_of")
  private MassOperationItemEntity retryOf;
}
