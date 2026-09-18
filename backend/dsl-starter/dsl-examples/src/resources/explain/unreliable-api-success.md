---
name: UnreliableApiSuccess
description: A process that routes an API call through the resilient transaction. Retries are expected to heal the failure, so the process succeeds and records that retries saved the call.
---

# UnreliableApiSuccess

This process is the happy path. You hand it an API call, and it passes that call to the resilient transaction. Because the resilient transaction retries several times, the helper usually succeeds on a later attempt. The process then reports `SUCCESS` and notes that retries healed the failure.

```mermaid
flowchart LR
    A[Start process] --> B[Run resilient transaction]
    B --> C{Helper succeeds?}
    C -->|no| D[Retry up to 4 times]
    D --> C
    C -->|yes| E[Return SUCCESS]
    E --> F[Process done]
```
