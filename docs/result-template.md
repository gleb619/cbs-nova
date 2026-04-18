# Result: {task-id}-{slug}

> Copy this file to `docs/results/{task-id}-{slug}.result.md` after User completes the task.
> Fill in all sections. This is the record that allows the next session to start cleanly.

---

## Identity

| Field        | Value                   |
|--------------|-------------------------|
| Task ID      | T__                     |
| Title        |                         |
| Status       | DONE / PARTIAL / FAILED |
| Session Date | YYYY-MM-DD              |

---

## Summary

> 3–5 sentences: what was built, how it works, what decisions were made that deviate from the spec.

---

## Changes Made

### Files Created

| File                   | Purpose              |
|------------------------|----------------------|
| `path/to/NewFile.java` | One-line description |

### Files Modified

| File                        | What changed       |
|-----------------------------|--------------------|
| `path/to/ExistingFile.java` | Added X, changed Y |

### Files Deleted

| File     | Reason |
|----------|--------|
| (if any) |        |

---

## Deviations from Spec

> List any places where User did something different from the task spec, and why.
> If none, write "None."

---

## Known Issues / Tech Debt

> Anything that was left TODO, is fragile, or needs follow-up in a future task.

- [ ] Issue 1
- [ ] Issue 2

---

## Acceptance Evidence

> Paste terminal output proving acceptance criteria were met.

```
# ./gradlew :backend:build
BUILD SUCCESSFUL in Xs

# ./gradlew :backend:test
X tests completed, 0 failed

# curl output or test log
```

---

## Impact on Plan

> List any task IDs in plan.md that are now unblocked because of this result.

- T__ is now unblocked (was waiting on this task)
- T__ is now unblocked

---

## Notes for Next Session

> Anything the next developer (or Claude) needs to know before picking up the next task.
> E.g.: "The `DslRegistry` singleton is initialized lazily on first access, not at startup — T11 must account for this."
