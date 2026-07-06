# Codex Loop Ideas Log

Candidate ideas considered by `docs/codex-loop.ignore.md` each fire. One row per candidate (chosen + rejected). Used for dedup and for mining — a rejected idea may become a winner when code reality shifts.

## Legend

| Column       | Meaning                                                                 |
|--------------|-------------------------------------------------------------------------|
| ID-proposed  | Task ID the idea *would* take if chosen (`T<next>` placeholder)         |
| Title        | Short idea name                                                         |
| Category     | `helper` / `integration` / `test` / `docs` / `perf` / `dx` / `example` / `refactor` / `ui` |
| Outcome      | `Chosen` (added to kanban) or `Rejected` (not this cycle)               |
| Reason       | One line — value/risk/novelty verdict, or dup source                    |

Re-pick rule: a `Rejected` row is eligible for revival only if its `Reason` was risk/novelty at fire time and a later code reality shows the blocker is gone. Pure dup rejects never revive.

## Candidates

| ID-proposed | Title | Category | Outcome | Reason |
|-------------|-------|----------|---------|--------|
