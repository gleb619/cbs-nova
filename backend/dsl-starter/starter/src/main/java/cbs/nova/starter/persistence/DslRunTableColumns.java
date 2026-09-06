package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;
import lombok.Value;

//TODO: redo to a record
public class DslRunTableColumns {

   Table table;
   TableColumn id;
   TableColumn runId;
   TableColumn processName;
   TableColumn status;
   TableColumn inputJson;
   TableColumn outputJson;
   TableColumn errorMessage;
   TableColumn contextJson;
   TableColumn startedAt;
   TableColumn finishedAt;
   TableColumn executionMode;
   TableColumn triggeredBy;
   TableColumn correlationId;

  public DslRunTableColumns(String name) {
    table = new Table(name);
    id = table.get("id");
    runId = table.get("run_id");
    processName = table.get("process_name");
    status = table.get("status");
    inputJson = table.get("input_json");
    outputJson = table.get("output_json");
    errorMessage = table.get("error_message");
    contextJson = table.get("context_json");
    startedAt = table.get("started_at");
    finishedAt = table.get("finished_at");
    executionMode = table.get("execution_mode");
    triggeredBy = table.get("triggered_by");
    correlationId = table.get("correlation_id");
  }

  TableReference refer() {
    return table.refer();
  }
}
