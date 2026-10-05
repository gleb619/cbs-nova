# API error-code catalog

Every failing API call returns the same unified envelope:
[`cbs.nova.dsl.model.ErrorResponse`](../backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/model/ErrorResponse.java).

## Envelope fields

| Field | Type | When it is set |
|---|---|---|
| `code` | `string` | Always. The machine-readable error classifier (this catalog). |
| `message` | `string` | Always. Human-readable explanation. |
| `entityName` | `string` | When the error is tied to a DSL definition, execution run, or notification rule. |
| `runId` | `string` | For runtime/preview errors that originate inside a DSL run. |
| `correlationId` | `string` | Copied from the `X-Correlation-Id` request header when supplied and valid. |
| `exceptionId` | `string` | For `DslException` paths: `{runId}:ex:{uuid}`. Use it to correlate with Sentry/Bugsink. |
| `diagnostics` | `[CompileDiagnostic]` | Compile/validation failures (`RELOAD_FAILED`, `DSL_COMPILATION_ERROR`, etc.). |
| `suggestion` | `string` | Preview-mode errors include a remediation hint. |
| `context` | `object` | Extra structured data (piece id, check type, SQL state, signal name, etc.). |

## Using `exceptionId` and `correlationId` for triage

- `correlationId` comes from the caller's `X-Correlation-Id` header. It is written to the logback
  MDC under key `cid` and to the response so a single client trace can be followed across requests.
- `exceptionId` is generated for `DslException` failures and is also available in the response.
  It is tagged in Sentry as `runId` and anchors the matching Bugsink breadcrumb event.
- Typical grep: `cid=<correlationId>` or `exceptionId=<exceptionId>` in the application logs, or
  search Sentry/Bugsink by `runId`.

## `diagnostics` shape for compile errors

`CompileDiagnostic` is a JSON object with these fields:

| Field | Type | Meaning |
|---|---|---|
| `file` | `string` | Source file path (may be `null`). |
| `line` | `integer` | Line number (may be `null`). |
| `column` | `integer | Column number (may be `null`). |
| `message` | `string` | Compiler/validator message. |
| `severity` | `string` | Usually `error`. |
| `code` | `string` | Compiler error code, if any. |

## Error-code table

Codes are grouped by the layer that emits them. HTTP status is the response status for the
failure path; codes that live inside a `200 OK` report (preview/hierarchy/explain/definition
tests) are marked **report**.

| Code | HTTP status | Where emitted | Meaning | Typical remediation |
|---|---|---|---|---|
| `BAD_REQUEST` | `400` | `DslExceptionHandler` (`IllegalArgumentException`), `DslScheduleHandler`, `DslDraftHandler` bundle validation, `DslPromoteHandler` bundle validation | Caller-supplied input is malformed or violates a business rule. | Fix the request body or query parameters; check the error message for the offending field. |
| `BAD_REQUEST` | `422` | `DslErrorCode.BAD_REQUEST` via `DefaultDslExceptionMapper` (`DslException`) | DSL runtime raised a bad-request classification. | See `DslException` logs; message describes the DSL-side failure. |
| `BUILDER_BUSY` | `429` | `DslExceptionHandler` (`BuilderClientBusyException`) | The DSL builder returned HTTP 429. | Back off and retry; check builder rate-limit configuration. |
| `BUILDER_ERROR` | varies | `BuilderApiErrorHandler` fallback (`Constants.FALLBACK_CODE`) | Builder returned an error body with no recognisable code. | Inspect the builder response and logs; verify builder health. |
| `BUILDER_UNAVAILABLE` | `503` | `DslExceptionHandler` (`BuilderUnavailableException`) | The DSL builder is unreachable or returned a 5xx. | Check builder service health and network path. |
| `BULKHEAD_SATURATED` | `503` | `DslFileHandler`, `DslDocsHandler` | Too many concurrent Git/file-system operations. | Reduce concurrency or increase bulkhead limits. |
| `CAPABILITY_DENIED` | `200` report | `PreviewErrorHandler` (`PreviewErrorCode`) | Preview/hierarchy/explain hit a manifest capability denial. | Add an object-target manifest piece that allow-lists the helper/capability, or disable object enforcement if intentional. |
| `COMPENSATION_ERROR` | `200` report | `PreviewErrorHandler` (`PreviewErrorCode`) | Compensation logic failed during preview. | Review compensation code for idempotency and side effects. |
| `COMPENSATION_FAILED` | `422` | `DslErrorCode.COMPENSATION_FAILED` via `DefaultDslExceptionMapper` | DSL runtime compensation step failed. | Check the `DslException` message and run logs. |
| `CONFLICT` | `409` | `DslExceptionHandler` (`ScheduleConflictException`), `DslExecutionsHandler` (cancel), `DslSignalsHandler` (not running), `ChangeRequestService`, `DslReloadHandler` directory missing | Request conflicts with current state. | Resolve the conflict (e.g. duplicate schedule, non-cancellable run, pending change request). |
| `DSL_COMPILATION_ERROR` | `200` report | `PreviewErrorHandler` (`PreviewErrorCode`) | DSL source failed compilation or validation during preview. | Review `diagnostics` for file/line/message details. |
| `ENTITY_NOT_FOUND` | `422` | `DslErrorCode.ENTITY_NOT_FOUND` via `DefaultDslExceptionMapper` | DSL runtime could not resolve an entity. | Verify the entity name and that it is compiled/loaded. |
| `ENV_NOT_FOUND` | `404` | `DslPromoteHandler` | Promotion environment is unknown or its directory is missing. | Check `cbs.dsl.promotion.environments.*` configuration. |
| `EXECUTION_FAILED` | `422` | `DslRuntimeMapper.fromThrowable` (runtime `run`) | A runtime DSL execution failed. | Check `runId`/`exceptionId` logs and Sentry. |
| `EXECUTION_FAILED` | `200` report | `DslRuntimeMapper.fromPreviewReport` fallback | Preview/hierarchy/explain report could not resolve a specific error code. | Inspect the full report `errors` array. |
| `EXTERNAL_CALL_FAILED` | `200` report | `PreviewErrorHandler` (`PreviewErrorCode`) | An external SQL/call failed during preview. | Inspect the target URL/JDBC URL, credentials, and network. |
| `FORBIDDEN` | `403` | `RbacAuthorizationFilter`, `PieceGuardFilter`, `DslDraftHandler` publish approval, `ChangeRequestHandler` | Caller lacks the required role, manifest pre-check failed, or publish requires operator approval. | Grant the required role, fix the manifest pre-check, or have an operator publish. |
| `GIT_REQUIRES_BUILDER` | `409` | `DslDraftVcsHandler`, `DslDraftSupport` | Git operations need a configured DSL builder. | Configure the builder client. |
| `HELPER_NOT_FOUND` | `200` report | `PreviewErrorHandler` (`PreviewErrorCode`) | A referenced `@Helper` is not registered or not on the classpath. | Register the helper or verify the classpath. |
| `INPUT_VALIDATION_ERROR` | `200` report | `PreviewErrorHandler` (`PreviewErrorCode`) | Preview input type/schema mismatch. | Match the input to the expected DSL input schema. |
| `INTERNAL_ERROR` | `500` | `DefaultDslExceptionMapper` (unhandled exception) | Unexpected server-side failure. | Check Sentry/Bugsink with `exceptionId`; escalate if persistent. |
| `INTERNAL_ERROR` | `422` | `DslErrorCode.INTERNAL_ERROR` via `DefaultDslExceptionMapper` | DSL runtime classified the failure as internal. | Check the `DslException` message and run logs. |
| `INVALID_CORRELATION_ID` | `400` | `RequestIdFilter`, `DslRuntimeHandler` | `X-Correlation-Id` header is invalid. | Supply a valid header matching `^[A-Za-z0-9_.:/-]+$` and max 200 chars. |
| `INVALID_IDEMPOTENCY_KEY` | `400` | `DslRuntimeHandler` | `Idempotency-Key` header format is invalid. | Use a valid key (see `IdempotencyKeys` rules). |
| `INVALID_REQUEST` | `400` | `DslRuntimeHandler`, `DslDraftHandler`, `DslPromoteHandler`, `DslDraftVcsHandler`, `ChangeRequestHandler`, `DslFileHandler`, `DslDocsHandler` | Generic malformed/missing request content. | Correct the JSON body or required path/query parameters. |
| `NOT_CONFIGURED` | `409` | `DslReloadHandler`, `DslDraftSupport`, `DslDraftVcsHandler`, `DslFileHandler`, `DslDocsHandler` | Required configuration (`cbs.dsl.source-dir` or builder) is missing. | Set the missing property or enable the feature. |
| `NOT_FOUND` | `404` | `DslExceptionHandler` (definition/api-key), `DslDraftHandler`, `DslDraftHistoryHandler`, `DslDraftVcsHandler`, `DslExecutionsHandler`, `DslSignalsHandler`, `NotificationRuleHandler`, `VhsManagementHandler`, `DslPromoteHandler` (environment directory), `DslReloadHandler` (source directory), `ChangeRequestService` | Requested resource does not exist. | Verify the identifier, path, or configuration value. |
| `PAYLOAD_TOO_LARGE` | `413` | `DslExceptionHandler` (`DslPayloadTooLargeException`) | Request body exceeds the configured DSL payload limit. | Reduce payload size or raise the limit. |
| `PIECE_BLOCKED` | `403` | `PieceGuardFilter` (post-check block) | The manifest piece is temporarily blocked for this principal. | Wait for the block to expire or contact an operator. |
| `PREVIEW_TIMEOUT` | `504` | `DslRuntimeMapper.fromPreviewTimeoutException` (runtime `run`) | Preview-style execution exceeded its timeout. | Increase the timeout or simplify the DSL. |
| `PREVIEW_TIMEOUT` | `200` report | `PreviewErrorHandler` (`PreviewErrorCode`) | Preview/hierarchy/explain exceeded its timeout. | Increase the preview timeout or simplify the DSL. |
| `RATE_LIMITED` | `429` | `RateLimitFilter` | Global rate-limit bucket exhausted for the client/IP/key. | Retry after `Retry-After`; reduce request rate. |
| `RELOAD_FAILED` | `500` | `DslReloadHandler` | DSL source reload failed at runtime. | Check `diagnostics` for compile errors and verify `cbs.dsl.source-dir`. |
| `SIGNAL_NOT_SEEDED` | `200` report | `PreviewErrorHandler` (`PreviewErrorCode`) | Preview awaited a signal that was not seeded. | Add the signal to the preview request's `signals` timeline. |
| `SIGNAL_PAYLOAD_TYPE_MISMATCH` | `200` report | `PreviewErrorHandler` (`PreviewErrorCode`) | Seeded signal payload JSON does not match the declared type. | Correct `payloadJson` to match `ProcessBuilder.signal(name, type)`. |
| `SUBSCRIBER_CAP_EXCEEDED` | `429` | `DslExceptionHandler` (`DslSubscriberCapExceededException`) | Too many active subscribers for the DSL run. | Reduce subscriber concurrency or raise the cap. |
| `TAPE_READ_ERROR` | `500` | `VhsManagementHandler` | VHS tape could not be read. | Check the tape file and storage. |
| `TEST_CASE_ERROR` | `200` report | `DslDefinitionTestService` | A definition test case threw an exception. | Inspect the test-case input and the DSL logic. |
| `TIMEOUT_EXCEEDED` | `200` report | `PreviewErrorHandler` (`PreviewErrorCode`) | A generic timeout occurred during preview. | Increase the timeout or simplify the DSL. |
| `UNAUTHORIZED` | `401` | `ApiKeyAuthFilter` | Missing or invalid `X-Api-Key`. | Provide a valid API key. |
| `UNKNOWN_ERROR` | `200` report | `PreviewErrorHandler` (`PreviewErrorCode`) | Unclassified preview failure. | Review the stack trace and recent changes. |
| `UNKNOWN_SIGNAL` | `400` | `DslSignalsHandler` | Signal name is not declared by the target process. | Use a signal declared in `ProcessBuilder.signal`. |
| `VALIDATION_FAILED` | `422` | `DslErrorCode.VALIDATION_FAILED` via `DefaultDslExceptionMapper` | DSL validation failed. | Correct the DSL source or input. |
| `VHS_LOAD_TEST_ERROR` | `400` | `VhsManagementHandler` | VHS load-test request is invalid. | Fix the load-test parameters. |

## Open / passthrough codes

The starter forwards some upstream codes verbatim rather than normalising them:

- **Builder passthrough:** `BuilderApiErrorHandler` maps builder HTTP 4xx/5xx responses to
  `BuilderApiException`, and `DslExceptionHandler` forwards `ex.getCode()`. Upstream builder error codes are **not a closed set**; they are intentionally out of scope of this catalog.
  If the builder body has no code, the fallback `BUILDER_ERROR` is used.
- **Preview/hierarchy/explain report passthrough:** `DslRuntimeMapper.fromPreviewReport` copies
  the first error's `code` from the report. That code is normally one of the
  `PreviewErrorCode` values above, but the report can also carry upstream builder codes.

## Known overlapping or inconsistent codes

> Do **not** rename codes in this task — clients may depend on the existing strings.
> A follow-up task should decide whether to reconcile these.

- `NOT_FOUND` (HTTP `404`, many handler paths) vs `ENTITY_NOT_FOUND` (HTTP `422`, `DslErrorCode`
  via `DslException`). Both mean "missing entity" but use different HTTP semantics.
- `BAD_REQUEST` is emitted as HTTP `400` for Spring/`IllegalArgumentException` paths and as
  HTTP `422` when it arrives through `DslErrorCode.BAD_REQUEST` from a `DslException`.
- `INTERNAL_ERROR` is emitted as HTTP `500` for unhandled exceptions and as HTTP `422` when
  classified as `DslErrorCode.INTERNAL_ERROR`.
- `COMPENSATION_FAILED` (`DslErrorCode`, runtime) vs `COMPENSATION_ERROR`
  (`PreviewErrorCode`, preview) — same concept, different vocabularies.
- `PREVIEW_TIMEOUT` appears both as a `200` report code and as a `504` runtime outcome code.

## OpenAPI schema

`ErrorResponse.code` is currently a bare `string` in `docs/openapi.json`. Adding an enum or
`@Schema(description=...)` annotation requires regenerating `docs/openapi.json` with
`make openapi`. That regeneration is currently blocked by **T767**; once T767 is resolved the
catalog link and a closed-vs-open note should be added to the annotation and the spec should be
regenerated.
