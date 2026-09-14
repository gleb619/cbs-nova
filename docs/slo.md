# cbs-nova SLOs

Three service-level objectives for the DSL runtime, using metrics that already ship from the
Spring Boot actuator (`/actuator/prometheus`, job `cbs-nova`).

All targets below are **placeholders** flagged with `<!-- TEAM: confirm target -->`. Tune them
against 28 days of real traffic, then remove the flags. Error-budget burn-rate alerts for
SLO 1 live in `app/compose/alerts.yml` (group `cbs-nova-slo-burn-rate`, T490).

Status labels: runs are tagged with the `DslRunStatus` enum
(`RUNNING, COMPLETED, FAILED, STALE, CANCELLED`). **Terminal statuses are
`COMPLETED|FAILED|STALE|CANCELLED`; `RUNNING` is excluded from every ratio.** Note there is
no `SUCCESS` label — successful runs are `status="COMPLETED"`.

Dashboard cross-ref: every SLI below is visible on the `cbs-nova-dsl-overview` Grafana
dashboard (provisioned by T481 from `app/compose/grafana/dashboards/dsl-overview.json`).

---

## SLO 1 — Run success rate

- **SLI** (window `1h` shown; use any rolling window up to 28d for reporting):

  ```promql
  sum(rate(dsl_run_count_total{status="COMPLETED"}[1h]))
  /
  sum(rate(dsl_run_count_total{status=~"COMPLETED|FAILED|STALE|CANCELLED"}[1h]))
  ```

- **Target**: **99.0%** of terminal runs complete with status `COMPLETED`.
  <!-- TEAM: confirm target -->
- **Window**: 28-day rolling.
- **Error budget**: `1 - target` = 1% of terminal runs ≈ **6.7 hours per 28 days**.
- **How we picked this**: starting value — revisit after 28d of real data. A 1% budget is the
  standard starting point for a workflow orchestrator where a failed run usually means a
  retryable Temporal workflow failure, not data loss.
- **Dashboard panel**: `cbs-nova-dsl-overview` → run-status breakdown / run-count panels.
- **Alerts**: `CbsNovaSloRunSuccessFastBurn` (page) and `CbsNovaSloRunSuccessSlowBurn`
  (ticket) in `app/compose/alerts.yml`, group `cbs-nova-slo-burn-rate`. These supersede the
  threshold-based `CbsNovaRunErrorRate` once tuned (kept as a coarser safety net for now).

## SLO 2 — Run latency (p95)

- **SLI**:

  ```promql
  histogram_quantile(0.95, sum by (le) (rate(dsl_run_duration_seconds_bucket[1h])))
  ```

- **Target**: p95 ≤ **60 s**. <!-- TEAM: confirm target -->
- **Window**: 28-day rolling.
- **Error budget**: share of runs whose duration exceeds the target (derived from the
  histogram once buckets exist).
- **How we picked this**: starting value — revisit after 28d of real data.
- **Note**: this timer is **end-to-end**, not just compute — it includes Temporal queue wait
  plus activity execution time from dispatch to terminal status, so the target must absorb
  Temporal-side queuing.
- **Prerequisite — read before trusting the SLI**: `dsl.run.duration` is a plain Micrometer
  Timer and percentile histograms are **not enabled** (no `management.metrics.distribution`
  config, no `publishPercentileHistogram`). Until T403 enables them, the `_bucket` series do
  **not exist** and `histogram_quantile` returns nothing. Until then the observable
  approximation is the mean: `sum(rate(dsl_run_duration_seconds_sum[1h])) /
  sum(rate(dsl_run_duration_seconds_count[1h]))` — watch it on the
  `cbs-nova-dsl-overview` dashboard and do not alert on p95 yet.
- **Dashboard panel**: `cbs-nova-dsl-overview` → run duration panel.
- **Alerts**: deferred — no burn-rate alert for this SLO in phase 1 (success-rate SLO
  carried the burn-rate work). Add once T403 lands and 28d of bucket data exists.

## SLO 3 — Preview latency (p95)

- **SLI**:

  ```promql
  histogram_quantile(0.95, sum by (le) (rate(dsl_preview_duration_seconds_bucket[1h])))
  ```

- **Target**: p95 ≤ **2 s**. <!-- TEAM: confirm target -->
- **Window**: 28-day rolling.
- **Error budget**: share of previews exceeding the target.
- **How we picked this**: starting value — revisit after 28d of real data. Deliberately
  tighter than the run-latency target: preview is the interactive authoring path (Workbench),
  where authors feel every second directly, unlike batch production runs.
- **Prerequisite**: same caveat as SLO 2 — `dsl.preview.duration` exports count/sum/max only
  until percentile histograms are enabled (T403). Interim observable is the mean
  (`dsl_preview_duration_seconds_sum` / `dsl_preview_duration_seconds_count`), already
  threshold-alerted by `CbsNovaPreviewLatencyP95`.
- **Dashboard panel**: `cbs-nova-dsl-overview` → preview duration panel.
- **Alerts**: deferred — same phase-1 deferral as SLO 2.

---

## Error budget policy

<!-- TEAM: fill in the actual policy (3-line placeholder). -->

When an SLO's error budget is burning at a rate that trips the fast-burn alert, the on-call
engineer pages, and the team freezes non-critical feature rollout and prioritises reliability
work on the affected path until the burn returns to normal. Slow-burn trips open a ticket
that is prioritised in the next planning cycle rather than interrupting anyone.
