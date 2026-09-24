package cbs.nova.starter.core;

import lombok.AccessLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StarterConstants {

  // Context attribute keys

  public static final String DSL_RESULT_ATTRIBUTE = "dslResult";
  public static final String EXECUTION_TRACE_ATTRIBUTE = "executionTrace";
  public static final String AST_TREE_ATTRIBUTE = "astTree";
  public static final String EXTERNAL_CALLS_ATTRIBUTE = "externalCalls";
  public static final String METRICS_ATTRIBUTE = "metrics";
  public static final String DRY_RUN_LOGS_ATTRIBUTE = "dryRunLogs";
  public static final String DRY_RUN_LOG_BUFFER_ATTRIBUTE = "dryRunLogBuffer";

  // Payload map keys

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

  // Audit action codes (handler → audit row)

  public static final String ACTION_TESTS_RUN = "TESTS_RUN";
  public static final String ACTION_DEFINITION_RELOAD = "DEFINITION_RELOAD";

  // Change-request approval gate (T568)

  public static final String ACTION_CHANGE_REQUEST_CREATE = "CHANGE_REQUEST_CREATE";
  public static final String ACTION_CHANGE_REQUEST_APPROVE = "CHANGE_REQUEST_APPROVE";
  public static final String ACTION_CHANGE_REQUEST_REJECT = "CHANGE_REQUEST_REJECT";
  public static final int CHANGE_REQUEST_COMMENT_MAX_LENGTH = 1024;

  public static final String ACTION_MANIFEST_RELOAD = "MANIFEST_RELOAD";
  public static final String ACTION_PIECE_GUARD_DENY = "PIECE_GUARD_DENY";
  public static final String ACTION_PIECE_POSTCHECK_FAILURE = "PIECE_POSTCHECK_FAILURE";

  // Correlation-id plumbing

  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  public static final String CORRELATION_ID_METADATA_KEY = "correlationId";

  // Micrometer metric names + tag keys

  public static final String EMPTY_OUTPUT_JSON = "{}";
  public static final String PURGED_COUNTER = "dsl.runs.purged";
  public static final String POSTCHECK_TOTAL_COUNTER = "dsl.piece.postcheck.total";
  public static final String POSTCHECK_FAILED_COUNTER = "dsl.piece.postcheck.failed";
  public static final String TRANSACTIONS_PURGED_COUNTER = "dsl.run.transactions.purged";
  public static final String RUN_DURATION_TIMER = "dsl.run.duration";
  public static final String RUN_COUNT_COUNTER = "dsl.run.count";
  public static final String CANCEL_COUNTER = "dsl.run.cancel";
  public static final String SWEEP_STALE_COUNTER = "dsl.run.sweep.stale";
  public static final String SWEEP_INSPECTED_COUNTER = "dsl.run.sweep.inspected";
  public static final String UNKNOWN_PROCESS = "unknown";
  public static final String PROCESS_NAME_TAG = "processName";
  public static final String STATUS_TAG = "status";

  // HTTP header names

  public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  // Security (role claim)

  public static final String DEFAULT_CLAIM_NAME = "roles";

  // Audit outcomes

  public static final String OUTCOME_SUCCESS = "SUCCESS";
  public static final String OUTCOME_FAILURE = "FAILURE";

  // Object-level capability enforcement (T552)

  public static final String CAPABILITY_DENIED_CODE = "CAPABILITY_DENIED";
  public static final String DSL_DEFINITION_NAME_METADATA_KEY = "cbs.nova.dsl.definitionName";
  public static final String ACTION_OBJECT_GUARD_DENY = "OBJECT_GUARD_DENY";
  public static final String ACTION_OBJECT_GUARD_PERMISSIVE = "OBJECT_GUARD_PERMISSIVE";
  public static final String OBJECT_GUARD_DENIED_COUNTER = "dsl.piece.object.denied";
  public static final String OBJECT_TYPE_TAG = "type";

  // Cancellation reasons

  public static final String CANCELLED_REASON = "Cancelled by user";

  // Micrometer metric names (reconciliation)

  public static final String INSPECTED_COUNTER = "dsl.run.reconciliation.inspected";
  public static final String RESOLVED_COUNTER = "dsl.run.reconciliation.resolved";

  // JDBC capture

  public static final String JDBC_FALLBACK_TARGET = "jdbc:datasource";

  // Maintenance task names + Micrometer meter names

  public static final String RUN_RETENTION_TASK_NAME = "run-retention";
  public static final String TASK_DURATION_TIMER = "dsl.maintenance.task.duration";
  public static final String TASK_PURGED_COUNTER = "dsl.maintenance.task.purged";
  public static final String TASK_TAG = "task";

  // Request-id plumbing

  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String REQUEST_ID_MDC_KEY = "rid";
  public static final String CORRELATION_ID_MDC_KEY = "cid";
  public static final String API_KEY_HEADER = "X-Api-Key";

  // Cache names (Caffeine caches)

  public static final String PREVIEW_RESULT = "previewResult";
  public static final String COMPENSATION_MARKERS = "compensationMarkers";
  public static final String UNRELIABLE_API_ATTEMPTS = "unreliableApiAttempts";
  public static final String DRY_RUN_LOG_BUFFERS = "dryRunLogBuffers";
  public static final String RUN_SCOPED_FAKE_CONFIGS = "runScopedFakeConfigs";
  public static final String MAP_INPUT_ADAPTERS = "mapInputAdapters";
  public static final String HELPER_INSTANCE_RESOLUTION = "helperInstanceResolution";
  public static final String INPUT_SCHEMA = "inputSchema";
  public static final String BUILDER_READS = "builderReads";

  // Preview Micrometer meter names

  public static final String CALL_COUNTER = "dsl.preview.calls";
  public static final String EXTERNAL_CALL_COUNTER = "dsl.preview.external.calls";
  public static final String DURATION_TIMER = "dsl.preview.duration";

  // Maintenance task names

  public static final String ORPHANS_TASK_NAME = "orphans";
  public static final String AUDIT_RETENTION_TASK_NAME = "audit-retention";

  // Maintenance configuration prefix

  public static final String DSL_MAINTENANCE_PREFIX = "dsl.maintenance";

  // Default numeric / sizing caps

  public static final int BUNDLE_FORMAT_VERSION = 1;
  public static final int DEFAULT_LIMIT = 50;
  public static final int HELPERS_DEFAULT_LIMIT = 100;
  public static final long DEFAULT_MAX_BYTES = 1024L * 1024L;
  public static final int DEFAULT_MAX_HUNKS = 200;

  // Default webhook / execution tuning

  public static final int DEFAULT_MAX_RETRIES = 3;
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
  public static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofSeconds(2);
  public static final int MAX_LIMIT = 500;
  public static final int DEFAULT_MAX_INPUT_OUTPUT_CHARS = 2000;
  // Misc default constants (moved from individual classes)
  public static final int DEFAULT_OFFSET = 0;
  public static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;
  public static final Instant NOT_FINISHED_AT = Instant.EPOCH;

  // SSE tunables (ExecutionSseService)

  public static final long SSE_EMITTER_TIMEOUT_MS = 0L;
  public static final long SSE_HEARTBEAT_SECONDS = 30L;

  // Security error codes (RbacAuthorizationFilter)

  public static final String FORBIDDEN_CODE = "FORBIDDEN";

  // Security error codes (PieceGuardFilter, T550 block-next-execution)

  public static final String PIECE_BLOCKED_CODE = "PIECE_BLOCKED";

  // Cache tunables (CompensationTrackerHelper)

  public static final Duration COMPENSATION_TRACKER_TTL = Duration.ofMinutes(5);
  public static final long COMPENSATION_TRACKER_MAX_SIZE = 10_000L;

  // Cache tunables (SpringOrGeneratedHelperInstanceResolver)

  public static final Duration HELPER_INSTANCE_CACHE_TTL = Duration.ofMinutes(1);
  public static final long HELPER_INSTANCE_CACHE_MAX_SIZE = 1_024L;

  // Cache tunables (UnreliableApiHelper)

  public static final Duration UNRELIABLE_API_TTL = Duration.ofMinutes(5);
  public static final long UNRELIABLE_API_MAX_SIZE = 10_000L;

  // File-latch cache tunables (FileLatchHelper)

  public static final long FILE_LATCH_WATCHER_POLL_MILLIS = 50L;
  public static final Duration FILE_LATCH_MONITORS_TTL = Duration.ofMinutes(10);
  public static final long FILE_LATCH_MONITORS_MAX_SIZE = 10_000L;

  // OpenTelemetry protocol constants (OpenTelemetryHelper)

  public static final String OTEL_INSTRUMENTATION_NAME = "cbs-nova-dsl";
  public static final String TRACEPARENT_VERSION = "00";
  public static final String TRACEPARENT_FLAGS_SAMPLED = "01";

  // YAML support caps (YamlSupport)

  public static final int YAML_MAX_CODE_POINTS = 3 * 1024 * 1024;

  // Workbench directory layout + bundle caps (DslDraftHandler)

  public static final String WORKBENCH_DRAFTS_DIR = ".workbench/drafts";
  public static final String WORKBENCH_PUBLISHED_DIR = ".workbench/published";
  public static final int BUNDLE_MAX_DEFINITIONS = 200;

  // Webhook protocol + caps (WebhookDispatcher, WebhookDeliveryRecordRepository)

  public static final String WEBHOOK_EVENT_RUN_COMPLETED = "run.completed";
  public static final String WEBHOOK_SIGNATURE_HEADER = "X-Cbs-Signature";
  public static final String WEBHOOK_TIMESTAMP_HEADER = "X-Cbs-Timestamp";
  public static final String WEBHOOK_EVENT_HEADER = "X-Cbs-Event";
  public static final int WEBHOOK_URL_MAX_LENGTH = 2048;
  public static final int WEBHOOK_LAST_ERROR_MAX_LENGTH = 1000;

  // OpenTelemetry propagator name (OpenTelemetryContextPropagator)

  public static final String OTEL_PROPAGATOR_NAME = "cbs-nova-otel-trace";

  // File suffix (DslDefinitionStatusResolver)

  public static final String JSON_SUFFIX = ".json";

  // Service shutdown grace (TemporalDslProcessService)

  public static final Duration SERVICE_SHUTDOWN_JOIN = Duration.ofSeconds(5);

  // Idempotency-key caps (IdempotencyKeys)

  public static final int IDEMPOTENCY_DERIVED_ID_MAX_LENGTH = 32;
  public static final int IDEMPOTENCY_MAX_KEY_LENGTH = 200;

  // OAuth/JWT scope claim names (RoleResolver)

  public static final String OAUTH_SCOPE_CLAIM = "scope";
  public static final String OAUTH_SCP_CLAIM = "scp";

  // Anonymous principal (DslAuditService)

  public static final String ANONYMOUS_PRINCIPAL = "anonymous";

  // API-key store crypto params (ApiKeyStore)

  public static final int API_KEY_RANDOM_BYTES = 32;
  public static final int API_KEY_PREFIX_LENGTH = 8;

  // Schedule service defaults (DslScheduleService)

  public static final String SCHEDULE_PREFIX = "sched-";
  public static final String DEFAULT_TIMEZONE = "UTC";

  // Worker shutdown grace (DslWorkerConfiguration)

  public static final long WORKER_TERMINATION_AWAIT_SECONDS = 10L;

  // JCE crypto params (AesFieldEncryptor)

  public static final String AES_ALGORITHM = "AES";
  public static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
  public static final int AES_GCM_IV_LENGTH = 12;
  public static final int AES_GCM_TAG_LENGTH = 128;

  // Rate-limit HTTP plumbing (RateLimitFilter)

  public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
  public static final String RETRY_AFTER_HEADER = "Retry-After";
  public static final String RATE_LIMITED_CODE = "RATE_LIMITED";
  public static final String RATE_LIMITED_MESSAGE = "Rate limit exceeded. Retry after the indicated delay.";

  // JSON Schema / JSON Pointer root (JsonSchemaValidator)

  public static final String JSON_POINTER_ROOT = "$";

  // OpenTelemetry service identity + env (TracingConfiguration)

  // Run-scoped fake config cache tunables (RunScopedFakeConfig)

  public static final Duration RUN_SCOPED_FAKE_TTL = Duration.ofHours(1);
  public static final long RUN_SCOPED_FAKE_MAX_SIZE = 1_024L;

  // External-call recorder caps (RunIdKeyedExternalCallRecorder)

  public static final int EXTERNAL_CALL_RECORDER_CAPACITY = 100;
  public static final int EXTERNAL_CALL_MAX_CALLS_PER_RUN = 100;

  // Random helper caps + charset pools (RandomHelper)

  public static final int RANDOM_MAX_STRING_LENGTH = 100_000;
  public static final String RANDOM_ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  public static final String RANDOM_ALPHA = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  public static final String RANDOM_NUMERIC = "0123456789";
  public static final String RANDOM_HEX = "0123456789abcdef";
  public static final String RANDOM_BASE64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

  // Builder cache key prefixes + scalar keys (BuilderCache)

  public static final String CACHE_HISTORY_PREFIX = "history:";
  public static final String CACHE_HISTORY_ENTRY_PREFIX = "historyEntry:";
  public static final String CACHE_HISTORY_DIFF_PREFIX = "historyDiff:";
  public static final String CACHE_DRAFT_PREFIX = "draft:";
  public static final String CACHE_DRAFTS_PAGE_PREFIX = "draftsPage:";
  public static final String CACHE_EXPORT_PREFIX = "export:";
  public static final String CACHE_FILES_PREFIX = "files:";
  public static final String CACHE_FILE_PREFIX = "file:";
  public static final String CACHE_FILE_EXISTS_PREFIX = "fileExists:";
  public static final String CACHE_PENDING_COUNT_KEY = "pendingCount";
  public static final String CACHE_VCS_STATUS_KEY = "vcsStatus";
  public static final String CACHE_VCS_LOG_PREFIX = "vcsLog:";
  public static final String CACHE_VCS_SHOW_PREFIX = "vcsShow:";

  // Semver regex fragments (SemverHelper)

  public static final String SEMVER_REGEX = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
          + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)"
          + "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
          + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$";
  public static final String SEMVER_IDENT_REGEX = "[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*";

  // JWT defaults (JwtHelper)

  public static final String JWT_DEFAULT_ALG = "HS256";
  public static final long JWT_DEFAULT_TTL_SECONDS = 3600L;

  // OpenAPI default version (OpenApiConfiguration)

  public static final String OPENAPI_DEFAULT_VERSION = "0.0.1-SNAPSHOT";

  // Executions handler CSV filename + timeseries tunables (DslExecutionsHandler)

  public static final String CSV_FILENAME_PATTERN = "yyyyMMdd-HHmmss";
  public static final int CSV_EXPORT_MAX_ROWS = 50_000;
  public static final String ACTION_RUN_CANCEL = "RUN_CANCEL";
  public static final int STATS_WINDOW_HOURS = 24;
  public static final int DEFAULT_TOP_PROCESSES = 5;
  public static final int MAX_TOP_PROCESSES = 20;
  public static final int TIMESERIES_DEFAULT_WINDOW_HOURS = 24;
  public static final int TIMESERIES_DEFAULT_BUCKET_MINUTES = 60;
  public static final int TIMESERIES_MIN_WINDOW_HOURS = 1;
  public static final int TIMESERIES_MAX_WINDOW_HOURS = 24 * 30;
  public static final int TIMESERIES_MIN_BUCKET_MINUTES = 1;
  public static final int TIMESERIES_MAX_BUCKET_MINUTES = 60 * 24;

  // Compile diagnostic column lengths (CompileDiagnosticRecordRepository)

  public static final int COMPILE_DIAGNOSTIC_SOURCE_MAX_LENGTH = 16;
  public static final int COMPILE_DIAGNOSTIC_DEFINITION_MAX_LENGTH = 256;
  public static final int COMPILE_DIAGNOSTIC_FILE_MAX_LENGTH = 512;
  public static final int COMPILE_DIAGNOSTIC_SEVERITY_MAX_LENGTH = 16;
  public static final int COMPILE_DIAGNOSTIC_CODE_MAX_LENGTH = 64;

  // Workbench history directory layout + suffix (DslDefinitionHistoryService)

  public static final String WORKBENCH_HISTORY_DIR = ".workbench/history";
  public static final String WORKBENCH_HISTORY_TIMESTAMP_PATTERN = "^[0-9]+$";

  // Run naming strategy default table (DslRunNamingStrategy)

  public static final String DSL_RUNS_DEFAULT_TABLE = "dsl_runs";

  // Correlation-id validation (CorrelationId)

  public static final int CORRELATION_ID_MAX_LENGTH = 200;
  public static final String CORRELATION_ID_VALID_PATTERN = "^[A-Za-z0-9_.:/-]+$";

  // Request-id filter error payload (RequestIdFilter)

  public static final String INVALID_CORRELATION_ID_CODE = "INVALID_CORRELATION_ID";
  public static final String INVALID_CORRELATION_ID_MESSAGE = "Invalid X-Correlation-Id header";

  // Preview error handler message prefixes (PreviewErrorHandler)

  public static final String UNKNOWN_ENTITY_PREFIX = "No DSL entity registered: ";
  public static final String HELPER_NOT_FOUND_PREFIX = "Helper not found: ";

  // CSV writer format constants (ExecutionCsvWriter)

  public static final char CSV_DELIMITER = ',';
  public static final char CSV_QUOTE = '"';
  public static final String CSV_LINE_ENDING = "\r\n";

  // Date formatting regex (FormatDateHelper)

  public static final String EPOCH_MILLIS_REGEX = "-?\\d+";

  // Line-diff context window (LineDiff)

  public static final int LINE_DIFF_CONTEXT_LINES = 3;

  // DSL file extension (DslFileRepository)

  public static final String JAVA_FILE_SUFFIX = ".java";

  // DSL reload staging prefix (DslReloadHandler)

  public static final String DSL_RELOAD_TEMP_PREFIX = "dsl-reload-";

  // JDBC dsl_api_keys columns (JdbcApiKeyRepository)

  public static final String DSL_API_KEY_COLUMNS = "id, label, key_hash, key_prefix, created_at, revoked_at, last_used_at";

  // Definition test status error code (DslDefinitionTestService)

  public static final String STATUS_TEST_CASE_ERROR = "TEST_CASE_ERROR";

  // JDBC dsl_definition_tests columns (DslDefinitionTestRepository)

  public static final String DSL_DEFINITION_TEST_COLUMNS = "id, definition_name, case_name, input, expected_output, created_at, updated_at";

  // JDBC dsl_events columns (DslEventRepository)

  public static final String DSL_EVENT_COLUMNS = "id, event_type, aggregate_type, aggregate_id, correlation_id, payload, schema_version, created_at";

  // External-call type tag for Kafka producer capture (MessagingCallCaptureProducer)

  public static final String TYPE_MESSAGING = "messaging";

  // JDBC capture: batch operation tag (RecordingStatement)

  public static final String OPERATION_BATCH = "BATCH";

  // VHS load-test metric names + tag keys

  public static final String VHS_REPLAY_DURATION_TIMER = "dsl.vhs.replay.duration";
  public static final String VHS_REPLAY_CALLS_COUNTER = "dsl.vhs.replay.calls";
  public static final String VHS_TARGET_TAG = "target";
  public static final String VHS_TAPE_TAG = "tape";
  public static final String VHS_PROCESS_TAG = "process";

  // Metric helper supported types (MetricHelper)

  public static final Set<String> METRIC_TYPES = Set.of("counter", "gauge", "timer", "summary");

  // Date-math unit vocabularies (DateMathHelper)

  public static final Set<String> DATE_MATH_ADD_UNITS = Set.of(
          "millis", "seconds", "minutes", "hours", "days", "weeks", "months", "years");
  public static final Set<String> DATE_MATH_START_OF_UNITS = Set.of(
          "minute", "hour", "day", "month", "year");
  public static final Set<String> DATE_MATH_DATE_ONLY_ADD_UNITS = Set.of(
          "days", "weeks", "months", "years");
  public static final Set<String> DATE_MATH_DATE_ONLY_START_OF_UNITS = Set.of(
          "day", "month", "year");

  // JDBC dsl_webhook_deliveries columns (WebhookDeliveryRecordRepository)

  public static final String DSL_WEBHOOK_DELIVERY_COLUMNS = "id, occurred_at, subscription_id, event_type, url, status, attempts, last_error, duration_ms";
}
