package cbs.nova.starter.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("dsl_run_transactions")
public class TransactionExecutionEntity {

  @Id
  private Long id;

  @Column("run_id")
  private String runId;

  @Column("transaction_name")
  private String transactionName;

  @Column("input_json")
  private String inputJson;

  @Column("executed_at")
  private Instant executedAt;
}
