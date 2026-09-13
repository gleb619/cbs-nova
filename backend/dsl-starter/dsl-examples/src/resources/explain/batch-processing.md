# BatchProcessing

Sums the `value` of every `BatchItem` in the input batch and returns a `BatchOut`
with the running total plus a comma-separated `id=value` summary of all items.

Runs as a side-effect-free in-process computation: no Temporal activity calls,
no compensations, and no external I/O.
