-- T768: plain started_at index. Every existing dsl_runs index leads with another column, so the
-- default (unfiltered) executions list `ORDER BY started_at DESC LIMIT n` and the stats/timeseries
-- `started_at >= ?` windows could not use one as a leading key. Measured on 300k seeded rows:
-- default list page seq scan (11.5k buffers + sort) -> index scan (5 buffers); 24h stats window
-- ~1.3k -> ~0.1k buffers. Additive, no data change.
CREATE INDEX IF NOT EXISTS idx_dsl_runs_started_at ON dsl_runs (started_at DESC);
