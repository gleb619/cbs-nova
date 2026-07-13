package cbs.nova.starter.repository;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

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

  @Column("started_at")
  private Instant startedAt;

  @Column("finished_at")
  private Instant finishedAt;

  @Column("execution_mode")
  private String executionMode;
}
