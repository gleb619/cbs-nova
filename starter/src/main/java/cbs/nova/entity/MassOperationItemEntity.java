package cbs.nova.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

  @Column(name = "mass_operation_execution_id")
  private Long massOperationExecutionId;

  /**
   * @deprecated use #{@link #massOperationExecutionId} instead
   */
  @Transient
  @Deprecated(forRemoval = true)
  private transient MassOperationExecutionEntity massOperationExecution;

  @Column(name = "item_key", nullable = false)
  private String itemKey;

  @Column(name = "item_data", nullable = false, columnDefinition = "jsonb")
  private String itemData;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MassOperationItemStatus status;

  @Column(name = "workflow_execution_id")
  private Long workflowExecutionId;

  /** @deprecated use #{@link #workflowExecutionId} instead */
  @Transient
  @Deprecated(forRemoval = true)
  private WorkflowExecutionEntity workflowExecution;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "retry_of")
  private Long retryOfId;

  /** @deprecated use #{@link #workflowExecutionId} instead */
  @Transient
  @Deprecated(forRemoval = true)
  private MassOperationItemEntity retryOf;
}
