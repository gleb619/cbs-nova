/**
 * Reduce a JSON value to its structural shape: keys + types, no values.
 *
 * Rules:
 *   - null            -> "null"
 *   - array           -> [mergedElementShape] (union over every element; empty -> [])
 *   - plain object    -> { [sorted keys]: shapeOf(child) }
 *   - everything else -> typeof value
 */
export function shapeOf(value: unknown): unknown {
  if (value === null) {
    return 'null'
  }

  if (Array.isArray(value)) {
    if (value.length === 0) {
      return []
    }
    return [value.map(shapeOf).reduce(mergeShape)]
  }

  if (typeof value === 'object') {
    const sorted: Record<string, unknown> = {}
    for (const key of Object.keys(value as Record<string, unknown>).sort()) {
      sorted[key] = shapeOf((value as Record<string, unknown>)[key])
    }
    return sorted
  }

  return typeof value
}

function mergeShape(a: unknown, b: unknown): unknown {
  if (a === null || b === null) {
    return a ?? b
  }

  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length === 0) return b
    if (b.length === 0) return a
    return [mergeShape(a[0], b[0])]
  }

  if (isPlainObject(a) && isPlainObject(b)) {
    const merged: Record<string, unknown> = { ...a }
    for (const key of Object.keys(b)) {
      if (!(key in merged)) {
        merged[key] = b[key]
      } else {
        merged[key] = mergeShape(merged[key], b[key])
      }
    }
    return merged
  }

  return a
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/**
 * Delete selected nodes from a shape object using JSON-path-ish dotted strings.
 *
 * Supported syntax:
 *   - "a.b.c"      -> delete key c inside object b inside object a
 *   - "a[].b"      -> delete key b from every element shape of array a
 *
 * Paths that do not match the provided shape are silently ignored.
 */
export function stripPaths(shape: unknown, paths: string[]): unknown {
  if (paths.length === 0 || shape === null || typeof shape !== 'object') {
    return shape
  }

  const working = deepClone(shape) as Record<string, unknown>

  for (const path of paths) {
    removePath(working, tokenize(path))
  }

  return working
}

function tokenize(path: string): string[] {
  return path.split('.').map((segment) => segment.replace(/\[\]$/, ''))
}

function removePath(node: unknown, segments: string[]): void {
  if (segments.length === 0 || node === null || typeof node !== 'object') {
    return
  }

  const [head, ...tail] = segments

  if (Array.isArray(node)) {
    if (node.length === 0) return
    if (tail.length === 0) return
    removePath(node[0], tail)
    return
  }

  if (!(head in node)) return

  if (tail.length === 0) {
    delete (node as Record<string, unknown>)[head]
    return
  }

  removePath((node as Record<string, unknown>)[head], tail)
}

function deepClone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}
