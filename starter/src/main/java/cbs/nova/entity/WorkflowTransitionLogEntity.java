package cbs.nova.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "workflow_transition_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowTransitionLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "workflow_execution_id")
  private Long workflowExecutionId;

  @Column(name = "event_execution_id")
  private Long eventExecutionId;

  /**
   * @deprecated use #{@link #workflowExecutionId} instead
   */
  @Transient
  @Deprecated(forRemoval = true)
  private WorkflowExecutionEntity workflowExecution;

  /**
   * @deprecated use #{@link #eventExecutionId} instead
   */
  @Transient
  @Deprecated(forRemoval = true)
  private EventExecutionEntity eventExecution;

  @Column(nullable = false, length = 20)
  private String action;

  @Column(name = "from_state", nullable = false, length = 100)
  private String fromState;

  @Column(name = "to_state", length = 100)
  private String toState;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "fault_message", columnDefinition = "TEXT")
  private String faultMessage;

  @Column(name = "dsl_version", nullable = false, length = 50)
  private String dslVersion;

  @Column(name = "performed_by", nullable = false, length = 200)
  private String performedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;
}
