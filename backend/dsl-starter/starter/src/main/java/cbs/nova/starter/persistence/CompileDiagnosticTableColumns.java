package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;

record CompileDiagnosticTableColumns(Table table, TableColumn id, TableColumn occurredAt,
        TableColumn source, TableColumn definition, TableColumn file, TableColumn line,
        TableColumn colNumber, TableColumn severity, TableColumn code, TableColumn message) {

  static CompileDiagnosticTableColumns of() {
    Table table = new Table("dsl_compile_diagnostics");
    TableColumn id = table.get("id");
    TableColumn occurredAt = table.get("occurred_at");
    TableColumn source = table.get("source");
    TableColumn definition = table.get("definition");
    TableColumn file = table.get("file");
    TableColumn line = table.get("line");
    TableColumn colNumber = table.get("col_number");
    TableColumn severity = table.get("severity");
    TableColumn code = table.get("code");
    TableColumn message = table.get("message");
    return new CompileDiagnosticTableColumns(table, id, occurredAt, source, definition, file,
            line, colNumber, severity, code, message);
  }

  TableReference refer() {
    return table().refer();
  }
}
