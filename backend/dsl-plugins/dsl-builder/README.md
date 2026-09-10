# DSL Builder Service

Spring Boot service that compiles cbs-nova DSL sources on demand and returns the generated
Temporal workflow/activity sources as a downloadable archive. It wraps the same compiler as
`dsl-gradle-plugin` (`cbs.nova.dsl`), but drives it through the Gradle Tooling API instead of
requiring a local Gradle build setup.

## How it works

1. `POST /api/dsl/compile` creates a compile session (UUID workspace under `workspace-dir`).
2. The service renders `settings.gradle` / `build.gradle` from classpath templates
   (`project/templates/`), applying the `cbs.nova.dsl` plugin with the requested
   `dslVersion` / `targetPackage` / `basePackage` / `buildVersion`.
3. Sources come from either or both of:
   - `repoUrl` (+ optional `baseBranch`) — a per-session git worktree is created from the
     configured repository clone, then `dsl/` and `models/` folders (under the configured
     `git.sub-path`, when set) are copied into the staged project;
   - `sources` — a map of file path → content written directly into the staged `src/`.
   When `repoUrl` is omitted, the configured `git.repo-url` repository is used.
4. A Gradle build (`clean build` by default) runs via the Gradle Tooling API with an
   auto-detected Java home (see `cbs.dsl.builder.gradle-java-home` to override).
5. On success the response contains the session `id` and the list of generated files.
6. `GET /api/dsl/compile/{id}/download` streams `build/generated` as a zip.

Sessions expire after `sessionTtl` (default 1h) and are cleaned up by a scheduled task.

## API

### `POST /api/dsl/compile`

```json
{
  "buildVersion": "v1",
  "targetPackage": "com.example.dsl",
  "basePackage": "com.example",
  "useFileNameSubPackage": true,
  "repoUrl": "https://github.com/example/dsl-sources.git",
  "baseBranch": "main",
  "sources": { "dsl/OrderFlow.java": "..." }
}
```

- Either `sources` or `repoUrl` must be provided (both may be combined).
- `targetPackage` / `basePackage` must be valid Java package names.
- Success: `200` with `{ "id", "success", "generatedFiles", "diagnostics", "durationMillis" }`.
- Failure: `422` with `{ "error": "COMPILE_FAILED", "diagnostics": [ ...gradle log tail... ] }`,
  or `400` for invalid requests.

### `GET /api/dsl/compile/{id}/download`

Streams `application/zip` of the generated output. `404` for unknown/expired sessions.

## Configuration (`cbs.dsl.builder.*`)

| Property            | Default                     | Description                                    |
|---------------------|-----------------------------|------------------------------------------------|
| `workspace-dir`     | `${java.io.tmpdir}/dsl-builder` | Root dir for compile session workspaces    |
| `cleanup-interval`  | `PT10M`                     | Scheduled session cleanup interval             |
| `session-ttl`       | `PT1H`                      | Session lifetime before cleanup                |
| `gradle-java-home`  | auto-detected               | JDK used for the Gradle build                  |
| `dsl-version`       | `0.0.1-SNAPSHOT`            | DSL platform version resolved from Maven Local |
| `temporal-version`  | `1.27.0`                    | `io.temporal:temporal-sdk` version             |
| `spring-boot-version` | `4.0.4`                   | Spring Boot BOM for starter model dependencies |
| `default-build-version` | `v1`                    | Fallback `buildVersion`                        |
| `build-tasks`       | `clean`, `build`            | Gradle tasks executed per compile              |
| `source-folders`    | `dsl`, `models`             | Repo folders copied into the staged project    |
| `templates-dir`     | `project/templates`         | Classpath location of the build templates      |
| `git.repo-url`      | —                           | Default git repository URL (per-request `repoUrl` wins) |
| `git.sub-path`      | —                           | Sub-folder inside the repo that holds the DSL sources   |
| `git.branch`        | remote default              | Branch cloned/pulled and used as worktree base |
| `git.repository-dir`| `<workspace-dir>/repo`      | Persistent clone of the configured repository  |
| `git.worktrees-dir` | `<workspace-dir>/worktrees` | Per-compile-session git worktrees              |

## Development

The service needs the DSL platform and the `dsl-gradle-plugin` in Maven Local first
(the staged build applies the plugin and resolves `dsl` / `dsl-api` from Maven Local):

```bash
cd backend
./gradlew -p dsl-platform publishToMavenLocal
./gradlew -p dsl-plugins :dsl-gradle-plugin:publishToMavenLocal
./gradlew -p dsl-plugins :dsl-builder:bootRun   # serves on port 8091
```

Tests: `./gradlew -p dsl-plugins :dsl-builder:test`

See also: [`dsl-gradle-plugin` README](../dsl-gradle-plugin/README.md),
[`docs/plans/dsl-builder-service.md`](../../../docs/plans/dsl-builder-service.md).
