# Configuration Reference

This page lists every configuration key exposed by the `cbs-nova` starter and the platform modules it depends on. Values
shown are the defaults taken from the code; always check the current `@ConfigurationProperties` classes for authoritative
defaults.

> **Environment variables:** keys can be written as env vars by upper-casing and replacing dots/kebab with underscores
> (e.g. `cbs.runs.retention` → `CBS_RUNS_RETENTION`). Spring relaxed binding also accepts camelCase and snake_case forms.

## Conventions

- **`0`-or-`false` disables escape hatches.** Several features are *off by default and opt-in*: rate limiting,
  OIDC, and the runs retention purge are all disabled unless you explicitly turn them on. Numeric caps
  (`cbs.runs.max-*`, `cbs.runs.retention`) treat `0` (or a negative value) as "disabled".
- **Opt-in security defaults-off.** `cbs.security.ratelimit.enabled` and
  `cbs.security.oidc.enabled` both default to `false` so a fresh starter is behaviourally identical to the
  historical anonymous, unthrottled starter until an operator opts in.
- Types use Spring's kebab-case keys. Durations are ISO-8601 (`PT5M`, `PT2S`) or plain `ms`/`s` suffixes and
  are bound as `java.time.Duration`.
- Booleans that guard features use `enabled` (e.g. `cbs.security.oidc.enabled`).
- Sizes/limits use `max-*` or `limit-*` (e.g. `cbs.runs.max-input-bytes`).

## `cbs.nova`

Core runtime and preview/explain settings.

| Key | Type | Default | Effect |
|-----|------|---------|--------|
| `cbs.nova.preview.execution.timeout-ms` | `long` | `20000` | Preview/Explain inline execution timeout. `0` disables the executor path. |
| `cbs.nova.preview.execution.pool-size` | `int` | `4` | Fixed thread pool size for preview/explain dispatch. |

## `cbs.security`

### OIDC (`OidcProperties`)

| Key | Type | Default | Effect |
|-----|------|---------|--------|
| `cbs.security.oidc.enabled` | `boolean` | `false` | Enables JWT resource-server mode. |
| `cbs.security.oidc.permit-all-paths` | `List<String>` | `/actuator/health/**` | Ant-style paths that are publicly accessible when OIDC is enabled. |

### Rate limiting (`RateLimitProperties`)

| Key | Type | Default | Effect |
|-----|------|---------|--------|
| `cbs.security.ratelimit.enabled` | `boolean` | `false` | Enables in-memory token-bucket rate limiting for mutating DSL routes. |
| `cbs.security.ratelimit.capacity` | `int` | `20` | Maximum token bucket capacity per IP. |
| `cbs.security.ratelimit.refill-rate` | `double` | `5.0` | Tokens added per second. |

## `cbs.health`

| Key | Type | Default | Effect |
|-----|------|---------|--------|
| `cbs.health.temporal.fail-status` | `String` | `none` | When Temporal is unreachable, `none` keeps `UP`; `down` makes the `dsl` health component `DOWN`. |
| `cbs.health.temporal.timeout` | `Duration` | `PT2S` | gRPC health-check timeout. |

## `cbs.runs`

> **⚠️ The `cbs.runs` prefix is registered by two classes.**

Both **`DslRunRetentionProperties`** and **`DslRunsProperties`** declare
`@ConfigurationProperties(prefix = "cbs.runs")` — i.e. *neither* uses `cbs.runs.retention` as its prefix. They
are two separately-bound beans that happen to share the root prefix, and each requires its own
`@EnableConfigurationProperties` / bean registration.

They currently own **disjoint leaf keys and do not collide**:

- `DslRunRetentionProperties` owns the **retention purge** knobs (`cbs.runs.retention`, `cbs.runs.purge-interval`,
  `cbs.runs.purge-batch-size`).
- `DslRunsProperties` owns the **payload size caps** (`cbs.runs.max-input-bytes`, `cbs.runs.max-output-bytes`).

**Hazard to watch:** because both bind the same prefix, authoring a new `cbs.runs.*` key requires deciding which
class owns it, and a future edit to one class (e.g. adding a field whose relaxed-bind name shadows a field in the
other) would silently break the sibling's binding. Ideally these should be merged into one `cbs.runs` class or
split onto distinct prefixes (e.g. `cbs.runs.retention.*` / `cbs.runs.purge.*`).

### `cbs.runs` — retention purge (`DslRunRetentionProperties`)

Follows the project's opt-in pattern: the purge is **disabled by default** until a positive retention is set.

| Key | Type | Default | Effect |
|-----|------|---------|--------|
| `cbs.runs.retention` | `Duration` | `Duration.ZERO` (`0`) | How long finished runs are kept before being purged. `0` or negative disables the scheduled purge entirely (no job registered, nothing deleted). |
| `cbs.runs.purge-interval` | `Duration` | `PT1H` | How often the scheduled purge runs. |
| `cbs.runs.purge-batch-size` | `int` | `500` | Max rows removed per batched delete pass (keeps each schema/row lock small). |

The purge deletes matching `dsl_run_transactions` rows together with their parent `dsl_runs`. Pre-existing
orphaned transaction rows are not auto-cleaned (a full-table anti-join would scan and lock the history table);
run a manual batched `NOT EXISTS` delete instead:

```sql
DELETE FROM dsl_run_transactions t
WHERE NOT EXISTS (SELECT 1 FROM dsl_runs r WHERE r.run_id = t.run_id);
```

### `cbs.runs` — payload size caps (`DslRunsProperties`)

| Key | Type | Default | Effect |
|-----|------|---------|--------|
| `cbs.runs.max-input-bytes` | `long` | `1048576` | Maximum allowed `POST /api/dsl/run/**` / `POST /api/dsl/preview/**` request body size. |
| `cbs.runs.max-output-bytes` | `long` | `10485760` | Maximum allowed serialized DSL output before it is rejected. |

## `dsl`

### Worker (`DslWorkerProperties`)

| Key | Type | Default | Effect |
|-----|------|---------|--------|
| `dsl.worker.enabled` | `boolean` | `true` | Start Temporal workers. Disable on pure preview/explain hosts. |
| `dsl.worker.task-queue` | `String` | `dsl-task-queue` | Default task queue used by generated workflows/activities. |
| `dsl.worker.namespace` | `String` | `default` | Temporal namespace. |
| `dsl.worker.host` | `String` | `localhost:7233` | Temporal frontend target. |
| `dsl.worker.identity` | `String` | `cbs-nova-worker` | Worker identity reported to Temporal. |
| `dsl.worker.max-concurrent-activity-execution-size` | `int` | `100` | Worker activity execution slot limit. |
| `dsl.worker.max-concurrent-workflow-task-execution-size` | `int` | `100` | Worker workflow task execution slot limit. |

### Reload / drafts (`DslReloadProperties`, `DslDraftProperties`)

| Key | Type | Default | Effect |
|-----|------|---------|--------|
| `dsl.reload.enabled` | `boolean` | `true` | Enables `POST /api/dsl/reload`. |
| `dsl.reload.source-dir` | `Path` | — | Directory to scan for `.java` DSL compact sources. |
| `dsl.drafts.enabled` | `boolean` | `true` | Enables Workbench draft routes. |
| `dsl.drafts.storage-dir` | `Path` | — | Directory where drafts are persisted. |

### Auth (`DslAuthProperties`)

| Key | Type | Default | Effect |
|-----|------|---------|--------|
| `dsl.auth.api-key` | `String` | `""` | When non-blank, every `/api/*` request must send the exact value in `X-Api-Key`. |

## Temporal & server env

Temporal connection values are usually supplied as environment variables rather than Spring properties because they
are infrastructure-specific:

| Env var | Typical value | Maps to |
|---------|---------------|---------|
| `TEMPORAL_HOST_URL` | `localhost:7233` | `dsl.worker.host` |
| `TEMPORAL_NAMESPACE` | `default` | `dsl.worker.namespace` |
| `TEMPORAL_TASK_QUEUE` | `dsl-task-queue` | `dsl.worker.task-queue` |

These can be set directly or bound through placeholders in `application.yml`:

```yaml
dsl:
  worker:
    host: ${TEMPORAL_HOST_URL:localhost:7233}
    namespace: ${TEMPORAL_NAMESPACE:default}
    task-queue: ${TEMPORAL_TASK_QUEUE:dsl-task-queue}
```
