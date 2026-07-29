import { describe, expect, it, vi } from 'vitest'
import { useDiffLines } from '../useDiffLines'

describe('useDiffLines', () => {
  it('marks every line as same for identical strings', () => {
    const result = useDiffLines('a\nb\nc', 'a\nb\nc').value

    expect(result).toEqual([
      { kind: 'same', text: 'a' },
      { kind: 'same', text: 'b' },
      { kind: 'same', text: 'c' },
    ])
  })

  it('marks extra lines on the left as lhs-only', () => {
    const result = useDiffLines('a\nb\nc', 'a\nc').value

    expect(result).toEqual([
      { kind: 'same', text: 'a' },
      { kind: 'lhs-only', text: 'b' },
      { kind: 'same', text: 'c' },
    ])
  })

  it('marks all lines as rhs-only when the left is empty', () => {
    const result = useDiffLines('', 'a\nb').value

    expect(result).toEqual([
      { kind: 'rhs-only', text: 'a' },
      { kind: 'rhs-only', text: 'b' },
    ])
  })

  it('marks all lines as lhs-only when the right is empty', () => {
    const result = useDiffLines('a\nb', '').value

    expect(result).toEqual([
      { kind: 'lhs-only', text: 'a' },
      { kind: 'lhs-only', text: 'b' },
    ])
  })

  it('interleaves same, lhs-only, and rhs-only lines', () => {
    const result = useDiffLines('a\nb\nc\nd', 'a\nx\nc\ny').value

    expect(result).toEqual([
      { kind: 'same', text: 'a' },
      { kind: 'lhs-only', text: 'b' },
      { kind: 'rhs-only', text: 'x' },
      { kind: 'same', text: 'c' },
      { kind: 'lhs-only', text: 'd' },
      { kind: 'rhs-only', text: 'y' },
    ])
  })

  it('falls back to raw diff and warns when combined line count exceeds 2000', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const lhs = Array.from({ length: 1100 }, (_, i) => `l${i}`).join('\n')
    const rhs = Array.from({ length: 1100 }, (_, i) => `r${i}`).join('\n')

    const result = useDiffLines(lhs, rhs).value

    expect(result.length).toBe(2200)
    expect(result[0]).toEqual({ kind: 'lhs-only', text: 'l0' })
    expect(result[1100]).toEqual({ kind: 'rhs-only', text: 'r0' })
    expect(warnSpy).toHaveBeenCalledWith(expect.stringContaining('useDiffLines'))

    warnSpy.mockRestore()
  })
})
