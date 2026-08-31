package cbs.nova.starter.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

//TODO: we need limit entity scan, only to one package `cbs.nova.starter.entity`
@Data
@Table("dsl_runs")
public class DslRunEntity {

  @Id
  private Long id;

  @Column("run_id")
  private String runId;

  @Column("process_name")
  private String processName;

  @Column("status")
  private String status;

  @Column("input_json")
  private String inputJson;

  @Column("output_json")
  private String outputJson;

  @Column("error_message")
  private String errorMessage;

  @Column("context_json")
  private String contextJson;

  @Column("started_at")
  private Instant startedAt;

  @Column("finished_at")
  private Instant finishedAt;

  @Column("execution_mode")
  private String executionMode;

  @Column("triggered_by")
  private String triggeredBy;

  @Column("correlation_id")
  private String correlationId;
}
