package cbs.nova.starter.core;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Central home for magic string constants shared across pipeline stages, capture interceptors, and
 * converters. Grouped by role: DSL context-attribute keys and external-call / dry-run-log payload
 * map keys.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StarterConstant {

  // --- Context attribute keys -------------------------------------------------------------

  public static final String DSL_RESULT_ATTRIBUTE = "dslResult";
  public static final String EXECUTION_TRACE_ATTRIBUTE = "executionTrace";
  public static final String AST_TREE_ATTRIBUTE = "astTree";
  public static final String EXTERNAL_CALLS_ATTRIBUTE = "externalCalls";
  public static final String METRICS_ATTRIBUTE = "metrics";
  public static final String DRY_RUN_LOGS_ATTRIBUTE = "dryRunLogs";
  public static final String DRY_RUN_LOG_BUFFER_ATTRIBUTE = "dryRunLogBuffer";

  // --- Payload map keys -------------------------------------------------------------------

  public static final String PAYLOAD_TYPE = "type";
  public static final String PAYLOAD_TARGET = "target";
  public static final String PAYLOAD_OPERATION = "operation";
  public static final String PAYLOAD_TIMESTAMP = "timestamp";
  public static final String PAYLOAD_METADATA = "metadata";
  public static final String PAYLOAD_METHOD = "method";
  public static final String PAYLOAD_URL = "url";
  public static final String PAYLOAD_BODY_LENGTH = "bodyLength";
  public static final String PAYLOAD_RUN_ID = "runId";
  public static final String PAYLOAD_MODE = "mode";
  public static final String PAYLOAD_INPUT = "input";
  public static final String PAYLOAD_TOPIC = "topic";
  public static final String PAYLOAD_KEY = "key";
  public static final String PAYLOAD_VALUE = "value";
  public static final String PAYLOAD_PARTITION = "partition";
  public static final String PAYLOAD_HEADERS = "headers";
  public static final String PAYLOAD_LEVEL = "level";
  public static final String PAYLOAD_MESSAGE = "message";
  public static final String PAYLOAD_MDC = "mdc";
}
