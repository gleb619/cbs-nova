import { type ComputedRef, computed } from 'vue'

export type DiffLineKind = 'same' | 'lhs-only' | 'rhs-only'

export interface DiffLine {
  kind: DiffLineKind
  text: string
}

function splitLines(value: string): string[] {
  return value.length === 0 ? [] : value.split('\n')
}

function buildLcsTable<T>(a: T[], b: T[]): number[][] {
  const m = a.length
  const n = b.length
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0))

  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = a[i - 1] === b[j - 1] ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1])
    }
  }

  return dp
}

export function useDiffLines(lhs: string, rhs: string): ComputedRef<DiffLine[]> {
  return computed(() => {
    const lhsLines = splitLines(lhs)
    const rhsLines = splitLines(rhs)

    if (lhsLines.length + rhsLines.length > 2000) {
      console.warn('useDiffLines: combined line count exceeds 2000, falling back to raw diff')
      return [
        ...lhsLines.map((text) => ({ kind: 'lhs-only' as const, text })),
        ...rhsLines.map((text) => ({ kind: 'rhs-only' as const, text })),
      ]
    }

    const dp = buildLcsTable(lhsLines, rhsLines)
    const result: DiffLine[] = []

    let i = lhsLines.length
    let j = rhsLines.length

    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && lhsLines[i - 1] === rhsLines[j - 1]) {
        result.unshift({ kind: 'same', text: lhsLines[i - 1] })
        i--
        j--
      } else if (i === 0 || (j > 0 && dp[i][j - 1] >= dp[i - 1][j])) {
        result.unshift({ kind: 'rhs-only', text: rhsLines[j - 1] })
        j--
      } else {
        result.unshift({ kind: 'lhs-only', text: lhsLines[i - 1] })
        i--
      }
    }

    return result
  })
}
