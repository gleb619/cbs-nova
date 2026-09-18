---
name: UnreliableApiUncaught
description: A process that routes an API call through the fragile transaction without any compensation. If the transaction fails, the process simply returns the failure.
---

# UnreliableApiUncaught

This process is the same as the compensated one, except it does not define any compensation action. It is useful for testing what happens when a failure is allowed to propagate without any cleanup. If the fragile transaction fails, the process returns the failure directly.
