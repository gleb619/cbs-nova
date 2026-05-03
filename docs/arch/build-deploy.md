# Build & Deploy Pipeline

← [Back to TDD](../tdd.md)

## 8. Build & Deploy Pipeline

### 8.1 Gitea: cbs-rules

- Stores `.kts` files only. No build logic, no app config.
- Repository: `cbs-rules`
- Branch strategy: `main` = production. Feature branches for new/changed rules.
- Import resolution and semantic validation enforced at compile time — broken imports, missing referenced
  events/helpers, undeclared transition target states all fail the build.

### 8.2 CI/CD Flow

```
cbs-rules Gitea: push to branch
  │
  └─► GitLab CI / Jenkins
        │
        ├─ Gradle: downloadDsl
        │    └─ Clone DSL branch from cbs-rules (fallback to main)
        │
        ├─ Gradle: compileDsl
        │    ├─ Compile all .kts files
        │    ├─ Resolve all #import declarations
        │    ├─ Semantic validation:
        │    │    ├─ All referenced events exist in registry
        │    │    ├─ All referenced helpers exist (code or inline)
        │    │    ├─ All transition target states declared in workflow states
        │    │    ├─ All condition references resolve
        │    │    └─ All transaction references resolve to known beans or DSL objects
        │    └─ Produce: dsl-rules-{version}.jar
        │
        ├─ Gradle: buildApp
        │    └─ Bundle dsl-rules JAR into application
        │
        └─ Docker build → push → deploy
```

### 8.3 Dev Mode

Dev mode changes **compilation only** — Temporal is still required and running. The dev endpoint skips the CI/CD compile
step and uses `javax.script` to evaluate `.kts` at runtime for fast feedback. State is persisted normally. Temporal is
invoked normally.

```
POST /dev/dsl/execute   (@Profile("dev") only)
{
  "dslContent": "event(\"TEST\") { ... }",
  "eventCode": "TEST",
  "action": "SUBMIT",
  "eventParameters": {},
  "userId": "dev-user"
}
```

---

Mass operation `.mass.kts` files are compiled by the same `compileDsl` Gradle task. Semantic validation is extended to
cover: all events referenced in `item { ctx -> }` exist in registry; all workflows referenced in `ctx.runWorkflow()`
exist in registry; all helpers used in `source {}`, `lock {}`, `context {}` resolve correctly; signal references (
`Signal.from("OP_CODE", ...)`) resolve to a known mass operation code.
