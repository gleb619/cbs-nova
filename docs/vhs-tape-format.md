# VHS Tape Format

Versioned JSON Lines format for cbs-nova execution recordings.
One tape represents exactly one recorded run.
This document defines the contract that downstream tasks **T555–T560** implement against;
no runtime code is specified here.

## File structure

A tape is a `.jsonl` file:

- Line 1 is a **header** describing the tape as a whole.
- Every subsequent line is an **event** in execution order.
- Each line is a single, self-contained JSON object with no line breaks inside the object.
- Lines are separated by a single LF (`\n`). A trailing newline is allowed but not required.

## Header line

The header must contain these exact fields:

| field | type | meaning |
|-------|------|---------|
| `vhs_tape_format_version` | string | Major tape-format version, e.g. `"1.0.0"`. |
| `schema_version` | string | Schema version for the event shape, e.g. `"1"`. |
| `recorded_at` | ISO-8601 string | Wall-clock time when the tape was created. |
| `source_run_id` | string | Unique id of the recorded run. |
| `correlation_id` | string | Correlation id propagated across calls. Reuses existing `correlation_id` / `rid` plumbing from Epic 4. |
| `route` | string | DSL route or entry point that produced the run, e.g. `POST /api/v1/dsl/run`. |

## Event line

Every event must contain these exact fields:

| field | type | meaning |
|-------|------|---------|
| `schema_version` | string | Must match `header.schema_version`. |
| `event_index` | integer | Zero-based position of the event in the tape. |
| `event_type` | enum string | One of `run_started`, `run_completed`, `call_start`, `call_end`, `trace`, `marker`, `error`. |
| `timestamp` | ISO-8601 string | Absolute wall-clock time of the event. |
| `relative_ms` | integer | Milliseconds elapsed since the `run_started` event. |
| `call_metadata` | object \| null | Required shape for `call_start` and `call_end`; may be `null` for other event types. |
| `input` | any | Input payload for the event; `null` when not applicable. |
| `output` | any | Output payload for the event; `null` when not applicable. |
| `timing` | object | `{ started_at, finished_at, duration_ms }`. |
| `correlation_id` | string | Same value as `header.correlation_id`. |
| `metadata` | object | Free-form extra context, e.g. `rid`, thread name, retry count. Empty object when unused. |

### `call_metadata`

Present on `call_start` / `call_end` events:

| field | type | meaning |
|-------|------|---------|
| `call_id` | string | Stable identifier linking the `call_start` / `call_end` pair. |
| `type` | string | Call category: `http`, `helper`, `function`, `process`, `temporal`. |
| `target` | string | URL, helper name, function name, process name, or Temporal action being invoked. |
| `operation` | string | Method or operation on the target, e.g. `GET`, `fetchCustomer`, `SignalName`. |

### `timing`

| field | type | meaning |
|-------|------|---------|
| `started_at` | ISO-8601 \| null | Event or call start time. |
| `finished_at` | ISO-8601 \| null | Event or call finish time; `null` until finished. |
| `duration_ms` | integer \| null | Elapsed milliseconds; `null` until finished. |

## Versioning policy

- `vhs_tape_format_version` is bumped only for **breaking** structural changes: removing the header line, renaming a required field, changing the line-oriented format, or removing an existing event field.
- `schema_version` is bumped for **additive, backwards-compatible** changes: adding optional event fields, adding new `event_type` values, or adding new `call_metadata` fields. Readers must ignore unknown fields.
- Consumers **T555–T560** should pin the `vhs_tape_format_version` they read and write, and reject tapes whose format version is higher than they understand.

## Worked example

A 6-line tape showing a header, run lifecycle, one HTTP call pair, a trace event, and run completion:

```jsonl
{"vhs_tape_format_version":"1.0.0","schema_version":"1","recorded_at":"2026-09-19T12:00:00Z","source_run_id":"run_2v7k9x","correlation_id":"corr_a1b2c3","route":"POST /api/v1/dsl/run"}
{"schema_version":"1","event_index":0,"event_type":"run_started","timestamp":"2026-09-19T12:00:00.000Z","relative_ms":0,"call_metadata":null,"input":null,"output":null,"timing":{"started_at":"2026-09-19T12:00:00.000Z","finished_at":null,"duration_ms":null},"correlation_id":"corr_a1b2c3","metadata":{"rid":"rid_a1b2c3"}}
{"schema_version":"1","event_index":1,"event_type":"call_start","timestamp":"2026-09-19T12:00:00.120Z","relative_ms":120,"call_metadata":{"call_id":"call_001","type":"http","target":"https://api.example.com/customers/42","operation":"GET"},"input":{"headers":{"X-Correlation-Id":"corr_a1b2c3"},"params":{"id":"42"}},"output":null,"timing":{"started_at":"2026-09-19T12:00:00.120Z","finished_at":null,"duration_ms":null},"correlation_id":"corr_a1b2c3","metadata":{}}
{"schema_version":"1","event_index":2,"event_type":"call_end","timestamp":"2026-09-19T12:00:00.340Z","relative_ms":340,"call_metadata":{"call_id":"call_001","type":"http","target":"https://api.example.com/customers/42","operation":"GET"},"input":null,"output":{"status":200,"body":{"id":"42","name":"Acme"}},"timing":{"started_at":"2026-09-19T12:00:00.120Z","finished_at":"2026-09-19T12:00:00.340Z","duration_ms":220},"correlation_id":"corr_a1b2c3","metadata":{}}
{"schema_version":"1","event_index":3,"event_type":"trace","timestamp":"2026-09-19T12:00:00.350Z","relative_ms":350,"call_metadata":null,"input":null,"output":null,"timing":{"started_at":"2026-09-19T12:00:00.350Z","finished_at":null,"duration_ms":null},"correlation_id":"corr_a1b2c3","metadata":{"message":"customer fetched"}}
{"schema_version":"1","event_index":4,"event_type":"run_completed","timestamp":"2026-09-19T12:00:00.900Z","relative_ms":900,"call_metadata":null,"input":null,"output":{"status":"OK"},"timing":{"started_at":"2026-09-19T12:00:00.000Z","finished_at":"2026-09-19T12:00:00.900Z","duration_ms":900},"correlation_id":"corr_a1b2c3","metadata":{}}
```
