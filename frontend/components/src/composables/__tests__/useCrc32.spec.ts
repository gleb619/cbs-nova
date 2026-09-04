import { describe, expect, it } from 'vitest'
import { calculateCrc32, formatCrc32, useCrc32 } from '../useCrc32'

describe('useCrc32', () => {
  it('returns 0 for null or empty input', () => {
    expect(calculateCrc32('')).toBe(0)
    expect(calculateCrc32(null)).toBe(0)
    expect(calculateCrc32(undefined)).toBe(0)
  })

  it('matches java.util.zip.CRC32 over ASCII strings', () => {
    expect(calculateCrc32('Hello World')).toBe(0x4a17b156)
    expect(formatCrc32(calculateCrc32('Hello World'))).toBe('4a17b156')
  })

  it('matches java.util.zip.CRC32 over UTF-8 strings', () => {
    // Japanese chars encoded as multi-byte UTF-8.
    expect(calculateCrc32('こんにちは')).toBe(0xb8841be7)
  })

  it('matches java.util.zip.CRC32 over a Uint8Array', () => {
    const bytes = new TextEncoder().encode('Hello World')
    expect(calculateCrc32(bytes)).toBe(0x4a17b156)
  })

  it('exposes the same functions via the composable', () => {
    const { calculateCrc32: calc, formatCrc32: fmt } = useCrc32()
    const hash = calc('Hello World')
    expect(hash).toBe(0x4a17b156)
    expect(fmt(hash)).toBe('4a17b156')
  })
})
