# Architecture Decision Records

This directory records the **why** behind cbs-nova's significant architectural decisions.
`architecture.md`, `architecture-backend.md`, and `architecture-ui.md` describe the system as it
is *now*; an ADR captures the forces at play when a call was made, the alternatives that were
rejected, and the consequences accepted — context that is otherwise lost the moment a PR merges.

## Format

Each ADR follows the [Michael Nygard format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions):

- **Title** — `NNNN. Short imperative phrase`
- **Status** — `Proposed` → `Accepted` (or `Rejected`) → optionally `Superseded by NNNN`
- **Context** — the forces at play: the problem, the constraints, what was true at the time
- **Decision** — the option chosen, stated in active voice ("We will …")
- **Consequences** — what results, *honestly* — positive, negative, and neutral

Keep it short. A few paragraphs per section is plenty. The value is in recording the trade-off,
not in exhaustiveness.

## Filename convention

`NNNN-short-slug.md` — zero-padded 4-digit number, monotonically increasing, never reused.
The number is an identifier, not a ranking.

## Status lifecycle

- A new ADR starts `Proposed` and becomes `Accepted` once the decision is acted on.
- A superseded decision keeps its ADR (marked `Superseded by NNNN`); it is never deleted.
  History is the point.

## When to write one

Write an ADR when a decision:

- changes a module boundary, a public interface, or the build/runtime contract, **or**
- picks one technology/pattern over a named alternative that a future reader would reasonably
  question, **or**
- constrains how future features must be built.

Routine implementation choices (which collection type, how to name a method, local refactors)
do **not** need an ADR.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-temporal-orchestration.md) | Use Temporal as the orchestration engine | Accepted |
| [0002](0002-helper-spi.md) | Wire `@Helper` implementations through a generated SPI, not reflection | Accepted |
| [0003](0003-bff-nitro-admin-ui-plugin.md) | Ship the admin UI's BFF as a Nitro layer inside the Nuxt module | Accepted |
