package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;

record ChangeRequestTableColumns(Table table, TableColumn id, TableColumn definitionName,
        TableColumn draftContent, TableColumn requestedBy, TableColumn requestedAt,
        TableColumn status, TableColumn approvedBy, TableColumn approvedAt, TableColumn comment) {

  static ChangeRequestTableColumns of() {
    Table table = new Table("dsl_change_request");
    TableColumn id = table.get("id");
    TableColumn definitionName = table.get("definition_name");
    TableColumn draftContent = table.get("draft_content");
    TableColumn requestedBy = table.get("requested_by");
    TableColumn requestedAt = table.get("requested_at");
    TableColumn status = table.get("status");
    TableColumn approvedBy = table.get("approved_by");
    TableColumn approvedAt = table.get("approved_at");
    TableColumn comment = table.get("comment");
    return new ChangeRequestTableColumns(table, id, definitionName, draftContent, requestedBy,
            requestedAt, status, approvedBy, approvedAt, comment);
  }

  TableReference refer() {
    return table().refer();
  }
}
