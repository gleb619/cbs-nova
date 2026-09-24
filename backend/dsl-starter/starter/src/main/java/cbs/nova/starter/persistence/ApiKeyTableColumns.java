package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;

public record ApiKeyTableColumns(Table table, TableColumn id, TableColumn label,
        TableColumn keyHash,
        TableColumn keyPrefix, TableColumn createdAt, TableColumn revokedAt,
        TableColumn lastUsedAt) {

  public static ApiKeyTableColumns of() {
    Table table = new Table("dsl_api_keys");
    TableColumn id = table.get("id");
    TableColumn label = table.get("label");
    TableColumn keyHash = table.get("key_hash");
    TableColumn keyPrefix = table.get("key_prefix");
    TableColumn createdAt = table.get("created_at");
    TableColumn revokedAt = table.get("revoked_at");
    TableColumn lastUsedAt = table.get("last_used_at");
    return new ApiKeyTableColumns(table, id, label, keyHash, keyPrefix, createdAt, revokedAt,
            lastUsedAt);
  }

  public TableReference refer() {
    return table().refer();
  }
}
