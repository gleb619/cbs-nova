---
name: unreliableApiTxResilient
description: A transaction that expects temporary failures. It calls the unreliable API helper and retries up to 4 times with exponential backoff. If all retries fail, it runs compensation logic.
---

# unreliableApiTxResilient

Imagine a delivery driver trying to drop a package at a house where nobody answers. Instead of giving up after the first knock, the driver knocks again — waiting a little longer each time — up to four tries. If nobody ever answers, the driver writes a failed-delivery note and leaves (compensation).

In DSL terms, this transaction calls the `unreliableApi` helper. Temporal retries the helper automatically using the configured retry policy: up to 4 retries with exponential backoff. If the helper finally returns success, the transaction succeeds. If the helper still fails, the transaction's compensation logic runs.
