package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;

record DslRunTableColumns(Table table, TableColumn id, TableColumn runId,
        TableColumn processName, TableColumn status, TableColumn inputJson,
        TableColumn outputJson, TableColumn errorMessage, TableColumn contextJson,
        TableColumn startedAt, TableColumn finishedAt, TableColumn executionMode,
        TableColumn triggeredBy, TableColumn correlationId, TableColumn definitionHash) {

  static DslRunTableColumns of(String name) {
    Table table = new Table(name);
    TableColumn id = table.get("id");
    TableColumn runId = table.get("run_id");
    TableColumn processName = table.get("process_name");
    TableColumn status = table.get("status");
    TableColumn inputJson = table.get("input_json");
    TableColumn outputJson = table.get("output_json");
    TableColumn errorMessage = table.get("error_message");
    TableColumn contextJson = table.get("context_json");
    TableColumn startedAt = table.get("started_at");
    TableColumn finishedAt = table.get("finished_at");
    TableColumn executionMode = table.get("execution_mode");
    TableColumn triggeredBy = table.get("triggered_by");
    TableColumn correlationId = table.get("correlation_id");
    TableColumn definitionHash = table.get("definition_hash");
    return new DslRunTableColumns(table, id, runId, processName, status, inputJson,
            outputJson, errorMessage, contextJson, startedAt, finishedAt, executionMode,
            triggeredBy, correlationId, definitionHash);
  }

  TableReference refer() {
    return table().refer();
  }
}
