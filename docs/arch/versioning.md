# Versioning Strategy

← [Back to TDD](../tdd.md)

## 10. Versioning Strategy

### 10.1 Principle

**Strict isolation. No interop.**

Every execution runs to completion on the DSL version it started with. `workflow_execution.dsl_version` is the authority. New instances always use the latest compiled version. Context is shared across transitions of the same instance regardless of version upgrades — the version locked at instance creation applies throughout.

### 10.2 Version Format

`{semver}-{gitCommitShort}` e.g. `1.5.0-a3f91bc`. Embedded in compiled JAR manifest.

### 10.3 Temporal Workflow ID

```
{eventCode}-{eventNumber}-{dslVersion}
```

Temporal `Workflow.getVersion()` guards against structural changes. Old workers drain in-flight workflows. New workers serve new starts.

---

Mass operations maintain strict version isolation even if a new DSL version deploys mid-run. The version is locked at execution creation and never updated.
