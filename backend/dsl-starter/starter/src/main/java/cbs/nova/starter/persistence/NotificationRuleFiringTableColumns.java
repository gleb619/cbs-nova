package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;

public record NotificationRuleFiringTableColumns(Table table, TableColumn id, TableColumn eventId,
        TableColumn ruleId, TableColumn ruleName, TableColumn sink, TableColumn outcome,
        TableColumn detail, TableColumn durationMs, TableColumn createdAt) {

  public static NotificationRuleFiringTableColumns of() {
    Table table = new Table("dsl_notification_rule_firing");
    TableColumn id = table.get("id");
    TableColumn eventId = table.get("event_id");
    TableColumn ruleId = table.get("rule_id");
    TableColumn ruleName = table.get("rule_name");
    TableColumn sink = table.get("sink");
    TableColumn outcome = table.get("outcome");
    TableColumn detail = table.get("detail");
    TableColumn durationMs = table.get("duration_ms");
    TableColumn createdAt = table.get("created_at");
    return new NotificationRuleFiringTableColumns(table, id, eventId, ruleId, ruleName, sink,
            outcome,
            detail, durationMs, createdAt);
  }

  public TableReference refer() {
    return table().refer();
  }
}
