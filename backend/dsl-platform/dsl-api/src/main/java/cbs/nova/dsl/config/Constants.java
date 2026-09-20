package cbs.nova.dsl.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constants {

  public static final String FALLBACK_CODE = "BUILDER_ERROR";
  public static final String FALLBACK_MESSAGE = "builder request failed";

  public static final String JAVA_FILE_SUFFIX = ".java";

  public static final String EMPTY_MARKDOWN = "<!-- NONE -->";

  public static final String EXPLAIN_BUDGET_CHARS_KEY = "explain.budgetChars";

  public static final String HIERARCHY_GRAPH_ACCUMULATOR_KEY = "hierarchy.graphAccumulator";

  public static final String CURRENT_OBJECT_NAME = "current.object_name";

  public static final String DSL_DEFINITION_NAME_METADATA_KEY = "cbs.nova.dsl.definitionName";

  public static final String CORRELATION_ID_METADATA_KEY = "correlationId";

  public static final String SIGNAL_AWAITER_METADATA_KEY = "cbs.nova.dsl.signalAwaiter";

  // JSON Schema draft URI (JacksonJsonSchemaGenerator)

  public static final String JSON_SCHEMA_DRAFT_URI = "https://json-schema.org/draft/2020-12/schema";

}
