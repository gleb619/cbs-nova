package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;

record DslDefinitionTestTableColumns(Table table, TableColumn id, TableColumn definitionName,
        TableColumn caseName, TableColumn input, TableColumn expectedOutput,
        TableColumn createdAt, TableColumn updatedAt) {

  static DslDefinitionTestTableColumns of() {
    Table table = new Table("dsl_definition_tests");
    TableColumn id = table.get("id");
    TableColumn definitionName = table.get("definition_name");
    TableColumn caseName = table.get("case_name");
    TableColumn input = table.get("input");
    TableColumn expectedOutput = table.get("expected_output");
    TableColumn createdAt = table.get("created_at");
    TableColumn updatedAt = table.get("updated_at");
    return new DslDefinitionTestTableColumns(table, id, definitionName, caseName, input,
            expectedOutput, createdAt, updatedAt);
  }

  TableReference refer() {
    return table().refer();
  }
}
