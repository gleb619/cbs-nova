package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;

public record DslEventTableColumns(Table table, TableColumn id, TableColumn eventType,
        TableColumn aggregateType, TableColumn aggregateId, TableColumn correlationId,
        TableColumn payload, TableColumn schemaVersion, TableColumn createdAt,
        TableColumn mqPublished) {

  public static DslEventTableColumns of() {
    Table table = new Table("dsl_events");
    TableColumn id = table.get("id");
    TableColumn eventType = table.get("event_type");
    TableColumn aggregateType = table.get("aggregate_type");
    TableColumn aggregateId = table.get("aggregate_id");
    TableColumn correlationId = table.get("correlation_id");
    TableColumn payload = table.get("payload");
    TableColumn schemaVersion = table.get("schema_version");
    TableColumn createdAt = table.get("created_at");
    TableColumn mqPublished = table.get("mq_published");
    return new DslEventTableColumns(table, id, eventType, aggregateType, aggregateId,
            correlationId, payload, schemaVersion, createdAt, mqPublished);
  }

  public TableReference refer() {
    return table().refer();
  }
}
