/**
 * Friendly aliases for the generated OpenAPI types.
 *
 * Consumers should import from here (`@cbs/components/types/api`) rather than
 * the raw `paths`/`components` tree in `./api.generated`.
 *
 * Note: the generated types mirror the spec exactly — the backend declares
 * almost no `required` fields, so most properties are optional even when the
 * backend always sends them.
 */
import type { components } from './api.generated'

type Schemas = components['schemas']

/** Inline compile diagnostics from `/api/dsl/reload|publish|preview`. */
export type CompileDiagnostic = Schemas['CompileDiagnostic']

/** Generic paged response wrapper (`items` is untyped in the spec — see Page<T> below). */
export type PageResponse = Omit<Schemas['PageResponse'], 'total' | 'offset'> & {
  total: number
  offset: number
}

/**
 * `PageResponse` as actually returned by list endpoints: the spec leaves
 * `items` as an empty schema, so specialise it at the call site, e.g.
 * `PageResponseOf<ExecutionDto>`.
 */
export type PageResponseOf<T> = Omit<PageResponse, 'items'> & { items: T[] }
