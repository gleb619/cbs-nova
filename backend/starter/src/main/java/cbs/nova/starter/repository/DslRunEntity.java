package cbs.nova.starter.repository;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("dsl_runs")
// TODO: move entity to another package, make schema for entity configurable
public class DslRunEntity {

  @Id
  private Long id;

  @Column("run_id")
  private String runId;

  @Column("process_name")
  private String processName;

  @Column("status")
  private String status;

  // TODO: add encryption/decryption, on app level
  @Column("input_json")
  private String inputJson;

  // TODO: add encryption/decryption, on app level
  @Column("output_json")
  private String outputJson;

  @Column("error_message")
  private String errorMessage;

  // TODO: add another column with json, with trace/ast/call tree about, where in dsl error was
  // thown

  @Column("started_at")
  private Instant startedAt;

  @Column("finished_at")
  private Instant finishedAt;

  @Column("execution_mode")
  private String executionMode;
}
