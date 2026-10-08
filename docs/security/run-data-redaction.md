# Run-data key-based redaction at egress (T930)

Run input and output are stored as JSON in `dsl_runs.input_json` / `output_json` and in VHS
tapes. T930 masks sensitive values when that data leaves the backend, while leaving the stored
rows untouched so replay, re-run, retention and reconciliation continue to work.

## What is masked

The redactor matches object keys against a case-insensitive regex list (default:
`password|passwd|secret|token|authorization|api[-_]?key|private[-_]?key|credential`). Matching
values are replaced with `***`. Matching is applied at every JSON object depth, inside arrays,
and inside JSON strings that themselves contain serialized JSON.

## Egress sites covered

| Surface | Class / method | What is redacted |
|---------|----------------|------------------|
| Execution list/detail API | `ExecutionDto.fromDetail` | `input` and `output` JSON trees |
| CSV export | `ExecutionCsvWriter.writeCsv` | `input`, `output`, and `error` columns |
| Transaction executions API | `DslExecutionsHandler.toDto` | transaction `input` object |
| VHS tape download | `VhsManagementHandler.download` / `redactJsonl` | every JSONL line of the tape body |
| Domain-event read API | `DslEventDto.from` | event `payload` JSON tree |
| Audit read API | `AuditDto.from` | `detailsJson` string |

## Sites deliberately not redacted

| Surface | Reason |
|---------|--------|
| VHS tape listing (`VhsManagementHandler.list`, `TapeSummary`) | The list only returns tape metadata (filename, run id, route, size, event count); it never includes run payloads. |
| VHS replay (`VhsReplayEngine`, `VhsTapeReader`, `LocalFileTapeStore`) | Internal read path. Replay must re-execute the original calls with the original secrets, so the tape file and reader are intentionally left raw. |
| Run re-run (`DslExecutionsHandler.rerun`) | Internal read path. Re-run reads stored `input_json` and starts a new execution from it; redaction would corrupt the new run. |
| Run retention / reconciliation / scheduler internals | These paths read `DslRun` rows to compute counts, purge old rows, or schedule new runs. They never serialize run payloads to operators. |
| Execution SSE (`ExecutionSseService`) | Events are `ExecutionStatusChangedEvent` (run id + status + timestamp) only; they never carry input/output. |
| Dry-run log SSE (`DryRunLogSseService`, `DryRunLogEvent`) | Log lines are free-text `message` + `mdc`, not structured run payloads. Masking arbitrary log text is out of scope for T930 and would destroy usability. |
| Audit write side (`DslAuditService.record`, handler `audit(...)` methods) | Audit details maps are built by handlers from safe identifiers (`runId`, `replayRunId`, etc.), not from raw run payloads. There is no generic egress of run data here. |
| DSL helper inputs/outputs during execution | Helpers execute with real secrets; redaction is only applied when values are projected out of the system. |

## Stored-in-clear caveat

Redaction is an egress control only. Run input/output remain stored in clear in `dsl_runs` and
VHS tapes unless encryption-at-rest (T879) is enabled. DSL authors should still avoid placing
live secrets in run input: use `httpAuth` headers, encrypted Temporal payloads, or a secrets manager.

## Search-by-payload

`DslRunRepository.search` only filters on `processName`, `status`, `mode`, and `correlationId`;
`input_json`, `output_json`, `context_json`, and `error_message` are not searchable. With
encryption-at-rest enabled (T879) this remains the only supported search shape — payload
content cannot be matched against ciphertext, and the repository does not attempt server-side
decryption to filter on it.
