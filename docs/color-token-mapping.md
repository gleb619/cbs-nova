# Color Token Mapping — raw Tailwind palette → semantic tokens

Companion to [`docs/colors.md`](./colors.md). Used by the phased palette migration
(T453 done, T457 / T458 pending). **Which vocabulary applies to which file is
decided in `docs/colors.md` §12** — read that first, then apply the matching table
below.

Two vocabularies exist (see `frontend/components/src/tailwind.config.ts`):

- **App chrome** — `primary` (terracotta), `neutral` (warm grey),
  `success` / `warning` / `error` / `info` (all 10-shade), `background` (flat).
- **Workbench** — `accent` (teal, **only `500` / `600`**), `ink` (`DEFAULT` +
  `muted` only), `line` / `surface` / `danger` (**all flat, no scale**).

## No-scale tokens — never write a numeric suffix

`background`, `line`, `surface`, `danger`, `ink`, `ink-muted`, and `accent`
outside `500` / `600` do **not** have a 10-step scale.
`bg-line-200`, `text-danger-600`, `bg-accent-100` are all invalid — they silently
produce no style.

- `ink` → `text-ink` (primary) / `text-ink-muted` (secondary)
- `line` → `border-line` / `divide-line`
- `surface` → `bg-surface`
- `danger` → `text-danger` / `border-danger` / `bg-danger`
- `accent` → `bg-accent-500`, `bg-accent-600` (hover/active), `ring-accent-500`,
  `text-accent-500`

## App-chrome files (T453 phase 1 pattern — unchanged)

| raw | semantic |
|---|---|
| `gray-{50…900}` | `neutral-{same step}` |
| `blue-50` | `primary-50` |
| `blue-300` | `primary-300` |
| `blue-600` / `blue-700` | `primary-600` / `primary-700` |
| `text-blue-700` | `text-primary-700` |
| `ring-blue-500` | `ring-primary-300` (focus-ring convention) |
| `red-{50,300,600,700}` | `error-{same}` |
| `yellow-{50,200,800}` | `warning-{same}` |
| `text-white` on a solid brand bg | keep `text-white` |

## Workbench files (T457 / T458)

Because `accent` / `ink` have no full scale, mapping is **by role**, not by number.

| raw | semantic | notes |
|---|---|---|
| `bg-gray-50` / `bg-gray-100` | `bg-surface` | panel / hover / disabled fill |
| `bg-white` panel | keep `bg-white` | elevated surface stays white |
| `border-gray-*` / `divide-gray-*` | `border-line` / `divide-line` | any border shade → the one `line` token |
| `text-gray-{400,500,600}` | `text-ink-muted` | secondary / caption text |
| `text-gray-{700,800,900}` | `text-ink` | primary text |
| `bg-blue-50` / `bg-blue-100` | `bg-accent-500/10` | tint via opacity, not a shade token |
| `bg-blue-{500,600}` / solid button | `bg-accent-500` (+ `hover:bg-accent-600`) | |
| `text-blue-*` / `border-blue-*` | `text-accent-500` / `border-accent-500` | |
| `ring-blue-500` | `ring-accent-500` | |
| `text-red-*` / `border-red-*` (inline error) | `text-danger` / `border-danger` | flat token |
| `bg-red-50` error banner fill | `bg-danger/10` | opacity tint |
| `red-*` used as a **status severity** (not just "error") | `error-{50,300,600,700}` | keep the scaled `error` family where shade nuance matters (pills, badges) — document per component |
| `yellow-*` | `warning-{same step}` | `warning` has a full scale |

**When `accent` needs a shade it doesn't have** (e.g. a `blue-200` divider):
prefer an opacity utility (`accent-500/20`) or fall back to `line` / `ink-muted`.
Do **not** add shades to `tailwind.config.ts` in T457/T458 — if a component
genuinely needs an `accent-300`, raise it (see `docs/colors.md` §12 open question).
