---
name: gradle-build
description: >
  Run Gradle tasks and return concise, actionable summaries of failures.
  Use for build/test/compile/dependency issues where raw logs are noisy.
  Keywords: gradle, build, compile, test, dependency, stacktrace, kotlin, java.
---

# Gradle Build Skill

## When to Use This Skill

- Gradle build failures (`build`, `check`, module tasks)
- Kotlin/Java compile errors from Gradle output
- Dependency resolution and plugin resolution failures
- Test task failures where root cause must be extracted quickly
- CI output triage when full logs are too large to read

## Command Standard

Always execute Gradle with stderr merged:

```bash
./gradlew <task> 2>&1
```

Examples:

- `./gradlew build 2>&1`
- `./gradlew test 2>&1`
- `./gradlew :module:compileKotlin 2>&1`

## Output Contract (Strict)

Return this exact structure:

```text
### Build Result
- Status: SUCCESS | FAILURE
- Task: <the task that was run>

### Errors (if any)
**Error 1:**
- Type: <ExceptionType or error category>
- Message: <human-readable message only>
- File: <relative/path/File.kt> (line X, col Y) — if available
- Cause: <root cause message, 1 line> — if available

### Warnings (optional, max 5 most relevant)
- <short warning message> — <file:line if available>

### What to fix
- <1–3 actionable suggestions based on the errors>
```

If build succeeds with no warnings, return only:

`✅ Build succeeded.`

## Parsing Rules

1. Extract high-signal failure text only:
   - Prioritize `* What went wrong:` block content
   - Include `Caused by:` message text without stack frames
   - Capture concise task/error markers (for example `error:`, `Could not resolve`)
2. Extract file references and normalize:
   - Accept forms like `src/.../Foo.kt: (12, 5)` or `/abs/path/File.java:[34,1]`
   - Convert absolute paths to project-relative paths when possible
   - Prefer output format `path/to/File.kt:line`
3. Ignore noisy log sections:
   - Stacktrace frames beginning with `at `
   - Gradle daemon/service chatter
   - Success/failure banners and repetitive task progress lines
   - Deprecation warnings unless they are directly causing failure
4. For compile failures:
   - Group related errors by file where possible
5. If more than 10 errors:
   - Show first 5 and add `...and X more errors. Fix the above first.`

## Fallback Rule

If parsing confidence is low, return only the concise `* What went wrong:` block
and avoid inventing details.

## Parent-Agent Wiring Pattern

Use this handoff prompt template:

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

## Re-run Strategy

- Use `./gradlew build --continue 2>&1` to collect multiple failures at once
- Use scoped reruns like `./gradlew :backend:test 2>&1` for tighter feedback loops
- Add verbosity only when needed (`--info`, `--stacktrace`) and keep summaries concise
- Prefer fixing top parseable errors first, then rerun to uncover downstream issues
