package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;

public record WebhookDeliveryTableColumns(Table table, TableColumn id, TableColumn occurredAt,
        TableColumn subscriptionId, TableColumn eventType, TableColumn url, TableColumn status,
        TableColumn attempts, TableColumn lastError, TableColumn durationMs) {

  public static WebhookDeliveryTableColumns of() {
    Table table = new Table("dsl_webhook_deliveries");
    TableColumn id = table.get("id");
    TableColumn occurredAt = table.get("occurred_at");
    TableColumn subscriptionId = table.get("subscription_id");
    TableColumn eventType = table.get("event_type");
    TableColumn url = table.get("url");
    TableColumn status = table.get("status");
    TableColumn attempts = table.get("attempts");
    TableColumn lastError = table.get("last_error");
    TableColumn durationMs = table.get("duration_ms");
    return new WebhookDeliveryTableColumns(table, id, occurredAt, subscriptionId, eventType, url,
            status, attempts, lastError, durationMs);
  }

  public TableReference refer() {
    return table().refer();
  }
}
