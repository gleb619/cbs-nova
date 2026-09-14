package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslRunEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.springframework.jdbc.core.RowMapper;

public class DslRunEntityRowMapper implements RowMapper<DslRunEntity> {

  @Override
  public DslRunEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
    DslRunEntity entity = new DslRunEntity();
    entity.setId(rs.getLong("id"));
    entity.setRunId(rs.getString("run_id"));
    entity.setProcessName(rs.getString("process_name"));
    entity.setStatus(rs.getString("status"));
    entity.setInputJson(rs.getString("input_json"));
    entity.setOutputJson(rs.getString("output_json"));
    entity.setErrorMessage(rs.getString("error_message"));
    entity.setContextJson(rs.getString("context_json"));
    Timestamp startedAt = rs.getTimestamp("started_at");
    entity.setStartedAt(startedAt != null ? startedAt.toInstant() : null);
    Timestamp finishedAt = rs.getTimestamp("finished_at");
    entity.setFinishedAt(finishedAt != null ? finishedAt.toInstant() : null);
    entity.setExecutionMode(rs.getString("execution_mode"));
    entity.setTriggeredBy(rs.getString("triggered_by"));
    entity.setCorrelationId(rs.getString("correlation_id"));
    entity.setDefinitionHash(rs.getString("definition_hash"));
    return entity;
  }
}
