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

import java.time.OffsetDateTime;

@Entity
@Table(name = "workflow_execution")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowExecutionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "workflow_code", nullable = false, length = 100)
  private String workflowCode;

  @Column(name = "dsl_version", nullable = false, length = 50)
  private String dslVersion;

  @Column(name = "current_state", nullable = false, length = 100)
  private String currentState;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private WorkflowStatus status;

  @Column(nullable = false, columnDefinition = "jsonb")
  private String context;

  @Column(name = "display_data", nullable = false, columnDefinition = "jsonb")
  private String displayData;

  @Column(name = "performed_by", nullable = false, length = 200)
  private String performedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
