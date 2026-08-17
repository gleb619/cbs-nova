package cbs.nova.starter.persistence;

import org.springframework.data.relational.core.mapping.NamingStrategy;

/**
 * Naming strategy that makes the {@link DslRunEntity} table name and schema configurable.
 */
public class DslRunNamingStrategy implements NamingStrategy {

  private static final String DEFAULT_TABLE = "dsl_runs";

  private final DslRunPersistenceProperties properties;

  public DslRunNamingStrategy(DslRunPersistenceProperties properties) {
    this.properties = properties;
  }

  @Override
  public String getTableName(Class<?> type) {
    if (DslRunEntity.class.equals(type)) {
      return qualifiedTableName();
    }
    return NamingStrategy.super.getTableName(type);
  }

  private String qualifiedTableName() {
    String table = properties.tableName() != null && !properties.tableName().isBlank()
            ? properties.tableName()
            : DEFAULT_TABLE;
    String schema = properties.schema();
    return (schema != null && !schema.isBlank()) ? schema + "." + table : table;
  }
}
