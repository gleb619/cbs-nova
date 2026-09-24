package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;

public record NotificationRuleTableColumns(Table table, TableColumn id, TableColumn name,
        TableColumn enabled, TableColumn eventType, TableColumn aggregateType,
        TableColumn aggregateIdPattern, TableColumn definitionPattern, TableColumn status,
        TableColumn actions, TableColumn priority, TableColumn rateClass, TableColumn createdAt,
        TableColumn updatedAt) {

  public static NotificationRuleTableColumns of() {
    Table table = new Table("dsl_notification_rule");
    TableColumn id = table.get("id");
    TableColumn name = table.get("name");
    TableColumn enabled = table.get("enabled");
    TableColumn eventType = table.get("event_type");
    TableColumn aggregateType = table.get("aggregate_type");
    TableColumn aggregateIdPattern = table.get("aggregate_id_pattern");
    TableColumn definitionPattern = table.get("definition_pattern");
    TableColumn status = table.get("status");
    TableColumn actions = table.get("actions");
    TableColumn priority = table.get("priority");
    TableColumn rateClass = table.get("rate_class");
    TableColumn createdAt = table.get("created_at");
    TableColumn updatedAt = table.get("updated_at");
    return new NotificationRuleTableColumns(table, id, name, enabled, eventType, aggregateType,
            aggregateIdPattern, definitionPattern, status, actions, priority, rateClass, createdAt,
            updatedAt);
  }

  public TableReference refer() {
    return table().refer();
  }
}
