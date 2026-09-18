---
name: unreliableApiTxFragile
description: A transaction that gives up quickly. It calls the unreliable API helper with only 1 retry. If it still fails, it runs compensation logic.
---

# unreliableApiTxFragile

This is the opposite of the resilient transaction. It knocks once, waits a moment, knocks one more time, and then gives up. It is useful for showing what happens when a service is too brittle to recover on its own.

It uses the same `unreliableApi` helper but with a retry policy that allows only a single retry. After that one extra attempt, any remaining failure triggers compensation.
