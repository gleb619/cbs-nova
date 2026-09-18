---
name: UnreliableApiCompensated
description: A process that routes an API call through the fragile transaction. The fragile transaction fails after its single retry, so the process compensates and records the failure for observability.
---

# UnreliableApiCompensated

This process shows the sad path. It sends the API call through the fragile transaction, which only retries once. If the helper still fails, the transaction fails and the process's compensation runs. The compensation records a marker via the `compensationTracker` helper so tests can verify the rollback happened.

```mermaid
sequenceDiagram
    participant P as Process
    participant T as Fragile transaction
    participant H as unreliableApi helper
    participant C as compensationTracker
    P->>T: run with apiCall
    T->>H: attempt 1
    H-->>T: failure
    T->>H: retry (attempt 2)
    H-->>T: failure
    T-->>P: failure
    P->>C: record compensation marker
```

This diagram shows two attempts, then compensation.
