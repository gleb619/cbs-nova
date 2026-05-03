---
name: gradle-build
description: Run Gradle commands and summarize failures into concise, actionable fixes. Use for build/test/compile/dependency issues where raw logs are noisy.
---

## When To Use

- Gradle build failures (`build`, `check`, module tasks)
- Kotlin/Java compile errors from Gradle output
- Dependency or plugin resolution failures
- CI build triage where full logs are too verbose

## Command Standard

Always execute with stderr merged:

```bash
./gradlew <task> 2>&1
```

Examples:

- `./gradlew build 2>&1`
- `./gradlew test 2>&1`
- `./gradlew :module:compileKotlin 2>&1`

## Required Output Structure

```text
### Build Result
- Status: SUCCESS | FAILURE
- Task: <the task that was run>

### Errors (if any)
**Error N:**
- Type: <ExceptionType or error category>
- Message: <human-readable error message only>
- File: <relative/path/to/File.kt> (line X, col Y) — if available
- Cause: <root cause message, 1 line> — if available

### Warnings (optional, max 5 most relevant)
- <short warning message> — <file:line if available>

### What to fix
- <1–3 actionable fix suggestions>
```

If the build succeeds with no relevant warnings, return only:

`✅ Build succeeded.`

## Parsing Rules

1. Prioritize high-signal failure content:
   - `* What went wrong:` block
   - `Caused by:` message text only
   - concise compiler/resolution errors (for example `error:`, `Could not resolve`)
2. Extract file references and normalize:
   - `src/main/kotlin/.../Foo.kt: (12, 5)`
   - `/absolute/path/File.java:[34,1]`
   - `Foo.kt:56`
   - Convert to project-relative path when possible
3. Ignore noise:
   - lines starting with `at ` (stack frames)
   - daemon/status chatter
   - repetitive task banners
   - deprecation notices unless they are build-blocking
4. Group compile errors by file when possible.
5. If there are more than 10 errors:
   - show first 5 and append `...and X more errors. Fix the above first.`

## Fallback Rule

If parsing confidence is low, return only the concise `* What went wrong:` block.

## Parent Agent Wiring

```python
subagent_prompt = """
Run the following Gradle command and return a structured error summary
following your instructions:

Command: ./gradlew build

Output:
\"\"\"
{raw_gradle_output}
\"\"\"
"""
```

## Re-run Guidance

- Use `./gradlew build --continue 2>&1` to collect multiple failures
- Re-run module/task-scoped commands for faster iteration
- Use `--info` if root cause is unclear
- Use `--stacktrace` only for deep diagnostics, then summarize without raw traces
