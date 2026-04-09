# Risks & Open Questions

← [Back to TDD](../tdd.md)

## 15. Risks & Open Questions

### 15.1 Risk Table

| Risk                         | Description                                                       | Severity | Mitigation                                                                            |
|------------------------------|-------------------------------------------------------------------|----------|---------------------------------------------------------------------------------------|
| Temporal worker versioning   | In-flight isolation                                               | High     | Spike before v1                                                                       |
| Helper idempotency           | Temporal retries may double side effects                          | High     | Convention in v1; framework in v2                                                     |
| Compensating transactions    | Wrong rollback() breaks ledger                                    | High     | Accounting/compliance review gate                                                     |
| Partial compensation failure | Rollback fails mid-way                                            | High     | Temporal retry on rollback; per-tx rollback status                                    |
| JSONB encryption performance | Encrypt/decrypt on every read/write                               | Medium   | Benchmark in staging                                                                  |
| prolong() loop risk          | Infinite prolong chain                                            | Medium   | Terminal state check; DSL author responsibility                                       |
| Mass op concurrency          | 80k items processed in parallel may overwhelm downstream services | High     | Configurable concurrency limit per mass operation (max parallel item activities)      |
| Mass op lock race condition  | Two scheduler instances fire simultaneously                       | Medium   | DB-level advisory lock or unique constraint on (code, status=RUNNING)                 |
| Signal delivery guarantee    | PARTIAL/COMPLETED signals must not be lost                        | Medium   | Persist signals to a `mass_operation_signal` table; retry on failure                  |
| resumeEvent misuse           | Calling resumeEvent when no prior execution exists                | Medium   | Framework validates workflow_execution exists before resumeEvent; throws if not found |

### 15.2 Resolved Decisions

- Context shared across transitions (no fresh seed between states)
- Preview confirmation: client calls SUBMIT independently
- Stakeholder funnel UI: v2
- prolong() depth guard: none
- compileDsl semantic validation: required, covers mass operations too
- Re-running entire mass operation: forbidden by framework
- Multiple runEvent per transition: async by default, await() to synchronize

### 15.3 Open Questions for Team Discussion

1. **Temporal worker versioning strategy**: Dedicated task queue per DSL version, or Temporal worker versioning API?
   Spike needed.
2. **Mass op concurrency limit**: Should it be declared in DSL, or configured per deployment environment?
3. **Signal persistence**: Where are emitted signals stored? Dedicated `mass_operation_signal` table, or reuse
   `workflow_transition_log`?
4. **Mass op lock implementation**: DB advisory lock vs. unique partial index on `(code, status='RUNNING')`?
5. **JSONB encryption strategy**: Field-level vs. row-level encryption? Affects query capability.
6. **Accounting review gate**: Who owns review of all `rollback()` implementations before go-live?
7. **BPMN export v1 scope**: Static template only, or dynamic instance view too?

---

### Resolved Design Decisions

The following questions from earlier drafts are now resolved:

- **Context inheritance**: Shared across transitions. No fresh seed between states.
- **Preview confirmation flow**: No server-side waiting. Client calls SUBMIT independently when satisfied.
- **Stakeholder funnel UI**: v2. Data model ready in v1.
- **ctx.prolong() depth guard**: None. DSL author's responsibility.
- **compileDsl semantic validation**: Required (not optional). All references validated at build time.
