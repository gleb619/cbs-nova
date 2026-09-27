import { describe, expect, it } from 'vitest'
import type { PersistedCompileDiagnostic } from '../types/dsl'
import {
  getDiagnosticCodeInfo,
  groupDiagnosticsByCode,
  type KnownDiagnosticCode,
} from '../useDiagnosticCodeRegistry'

describe('useDiagnosticCodeRegistry', () => {
  describe('getDiagnosticCodeInfo', () => {
    it('resolves every known diagnostic code with its registry entry and isKnown=true', () => {
      const knownCodes: KnownDiagnosticCode[] = [
        'BLANK_PROCESS_NAME',
        'BLANK_TRANSACTION_NAME',
        'BLANK_FUNCTION_NAME',
        'DUPLICATE_NAME',
        'UNKNOWN_HELPER',
        'CIRCULAR_DEPENDENCY',
      ]

      for (const code of knownCodes) {
        const result = getDiagnosticCodeInfo(code)
        expect(result.isKnown).toBe(true)
        expect(result.label).not.toBe(code)
        expect(result.severity).toBe('error')
        expect(result.hint).not.toBe('')
      }
    })

    it('returns label, isKnown=false and empty hint for an unknown code', () => {
      const result = getDiagnosticCodeInfo('SOME_UNKNOWN_CODE')
      expect(result).toEqual({
        label: 'SOME_UNKNOWN_CODE',
        severity: 'error',
        hint: '',
        isKnown: false,
      })
    })

    it('uses fallback severity for unknown codes when valid', () => {
      expect(getDiagnosticCodeInfo('X', 'warning').severity).toBe('warning')
      expect(getDiagnosticCodeInfo('X', 'INFO').severity).toBe('info')
      expect(getDiagnosticCodeInfo('X', 'Error').severity).toBe('error')
    })

    it('normalizes invalid fallback severity to error for unknown codes', () => {
      expect(getDiagnosticCodeInfo('X', 'oops').severity).toBe('error')
      expect(getDiagnosticCodeInfo('X', null).severity).toBe('error')
      expect(getDiagnosticCodeInfo('X', undefined).severity).toBe('error')
    })

    it('returns Unknown diagnostic label when code is null or undefined', () => {
      expect(getDiagnosticCodeInfo(null).label).toBe('Unknown diagnostic')
      expect(getDiagnosticCodeInfo(undefined).label).toBe('Unknown diagnostic')
    })

    it('ignores fallback severity for known codes', () => {
      expect(getDiagnosticCodeInfo('DUPLICATE_NAME', 'warning').severity).toBe('error')
    })
  })

  describe('groupDiagnosticsByCode', () => {
    function makeDiagnostic(
      code: string | null,
      severity: string,
      overrides?: Partial<PersistedCompileDiagnostic>,
    ): PersistedCompileDiagnostic {
      return {
        id: 1,
        occurredAt: '2026-09-27T00:00:00Z',
        source: 'compiler',
        definition: 'test',
        severity,
        code,
        message: 'message',
        ...overrides,
      }
    }

    it('groups items by code and sorts groups alphabetically', () => {
      const items = [
        makeDiagnostic('UNKNOWN_HELPER', 'warning', { id: 1 }),
        makeDiagnostic('DUPLICATE_NAME', 'error', { id: 2 }),
        makeDiagnostic('UNKNOWN_HELPER', 'warning', { id: 3 }),
      ]

      const groups = groupDiagnosticsByCode(items)
      expect(groups.map((g) => g.code)).toEqual(['DUPLICATE_NAME', 'UNKNOWN_HELPER'])
      expect(groups[0].items).toHaveLength(1)
      expect(groups[1].items).toHaveLength(2)
    })

    it('uses UNKNOWN_CODE bucket for missing codes', () => {
      const items = [makeDiagnostic(null, 'warning', { id: 1 })]

      const groups = groupDiagnosticsByCode(items)
      expect(groups.map((g) => g.code)).toEqual(['UNKNOWN_CODE'])
      expect(groups[0].info).toEqual({
        label: 'UNKNOWN_CODE',
        severity: 'warning',
        hint: '',
        isKnown: false,
      })
    })

    it('resolves group info from the first item severity for unknown codes', () => {
      const items = [makeDiagnostic('CUSTOM', 'info', { id: 1 })]

      const result = groupDiagnosticsByCode(items)[0]
      expect(result.info).toEqual({
        label: 'CUSTOM',
        severity: 'info',
        hint: '',
        isKnown: false,
      })
    })

    it('resolves known code info even when severity differs', () => {
      const items = [makeDiagnostic('CIRCULAR_DEPENDENCY', 'warning', { id: 1 })]

      const result = groupDiagnosticsByCode(items)[0]
      expect(result.info.isKnown).toBe(true)
      expect(result.info.label).toBe('Circular dependency')
      expect(result.info.severity).toBe('error')
    })
  })
})
