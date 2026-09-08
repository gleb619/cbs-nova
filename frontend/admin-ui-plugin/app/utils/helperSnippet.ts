// T400 — helper search panel insert-at-cursor.
//
// A cookbook deep-link was considered and dropped: `docs/dsl/helpers.md` groups
// recipes by task rather than by helper name, so there is no reliable `#<name>`
// anchor to link to.
export interface HelperSnippetSource {
  name: string
  inputType?: string | null
  outputType?: string | null
}

/**
 * Build the `ctx.runHelper(...)` reference for a search result.
 *
 * Missing input type becomes a `null` argument; missing output type drops the
 * `.as(...)` cast. The grammar is identical in Process / Transaction / Function
 * bodies, so no per-construct branching is needed.
 */
export function buildHelperSnippet(result: HelperSnippetSource): string {
  const inputType = result.inputType?.trim()
  const outputType = result.outputType?.trim()
  const call = `ctx.runHelper("${result.name}", ${inputType ? `new ${inputType}()` : 'null'})`
  return outputType ? `${call}.as(${outputType}.class)` : call
}
