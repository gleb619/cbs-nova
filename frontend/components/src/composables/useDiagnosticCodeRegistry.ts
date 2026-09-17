import type { PersistedCompileDiagnostic } from '../types/dsl'

export type KnownDiagnosticCode =
  | 'BLANK_PROCESS_NAME'
  | 'BLANK_TRANSACTION_NAME'
  | 'BLANK_FUNCTION_NAME'
  | 'DUPLICATE_NAME'
  | 'UNKNOWN_HELPER'
  | 'CIRCULAR_DEPENDENCY'

export interface DiagnosticCodeInfo {
  label: string
  severity: 'error' | 'warning' | 'info'
  hint: string
}

const registry: Record<KnownDiagnosticCode, DiagnosticCodeInfo> = {
  BLANK_PROCESS_NAME: {
    label: 'Blank process name',
    severity: 'error',
    hint: 'Provide a non-empty process name.',
  },
  BLANK_TRANSACTION_NAME: {
    label: 'Blank transaction name',
    severity: 'error',
    hint: 'Provide a non-empty transaction name.',
  },
  BLANK_FUNCTION_NAME: {
    label: 'Blank function name',
    severity: 'error',
    hint: 'Provide a non-empty function name.',
  },
  DUPLICATE_NAME: {
    label: 'Duplicate name',
    severity: 'error',
    hint: 'Ensure each definition has a unique name.',
  },
  UNKNOWN_HELPER: {
    label: 'Unknown helper',
    severity: 'error',
    hint: 'Check that the helper exists and is imported.',
  },
  CIRCULAR_DEPENDENCY: {
    label: 'Circular dependency',
    severity: 'error',
    hint: 'Break the circular reference between definitions.',
  },
}

export interface ResolvedDiagnosticCode extends DiagnosticCodeInfo {
  isKnown: boolean
}

function normalizeSeverity(
  severity: string | null | undefined,
): 'error' | 'warning' | 'info' | null {
  const value = severity?.toLowerCase()
  if (value === 'error' || value === 'warning' || value === 'info') return value
  return null
}

export function getDiagnosticCodeInfo(
  code: string | null | undefined,
  fallbackSeverity?: string | null,
): ResolvedDiagnosticCode {
  if (code && code in registry) {
    return { ...registry[code as KnownDiagnosticCode], isKnown: true }
  }
  return {
    label: code || 'Unknown diagnostic',
    severity: normalizeSeverity(fallbackSeverity) ?? 'error',
    hint: '',
    isKnown: false,
  }
}

export interface DiagnosticCodeGroup {
  code: string
  info: ResolvedDiagnosticCode
  items: PersistedCompileDiagnostic[]
}

export function groupDiagnosticsByCode(items: PersistedCompileDiagnostic[]): DiagnosticCodeGroup[] {
  const buckets = new Map<string, PersistedCompileDiagnostic[]>()
  for (const item of items) {
    const key = item.code || 'UNKNOWN_CODE'
    const list = buckets.get(key) ?? []
    list.push(item)
    buckets.set(key, list)
  }
  return Array.from(buckets.entries())
    .map(([code, groupItems]) => ({
      code,
      info: getDiagnosticCodeInfo(code, groupItems[0]?.severity),
      items: groupItems,
    }))
    .sort((a, b) => a.code.localeCompare(b.code))
}
