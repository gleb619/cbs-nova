import { describe, expect, it, vi } from 'vitest'
import { useHelperCompletion } from '../useHelperCompletion'

const sampleCatalog = [
  {
    name: 'uuidV7',
    description: 'Time-ordered UUID',
    inputType: 'UuidV7In',
    outputType: 'UuidV7Out',
    hasSideEffects: false,
  },
  { name: 'base64', description: 'Encode/decode base64', hasSideEffects: false },
]

describe('useHelperCompletion', () => {
  it('fetches the catalog on first call', async () => {
    const fetch = vi.fn().mockResolvedValue(sampleCatalog)
    const { getCatalog } = useHelperCompletion({ fetch })

    const result = await getCatalog()

    expect(fetch).toHaveBeenCalledTimes(1)
    expect(result).toBe(sampleCatalog)
  })

  it('caches the catalog and returns it on subsequent calls without re-fetching', async () => {
    const fetch = vi.fn().mockResolvedValue(sampleCatalog)
    const { getCatalog } = useHelperCompletion({ fetch })

    const first = await getCatalog()
    const second = await getCatalog()

    expect(fetch).toHaveBeenCalledTimes(1)
    expect(second).toStrictEqual(first)
  })

  it('coalesces concurrent getCatalog calls into a single fetch', async () => {
    let resolveFetch: (value: typeof sampleCatalog) => void = () => {}
    const fetch = vi.fn(
      () =>
        new Promise<typeof sampleCatalog>((resolve) => {
          resolveFetch = resolve
        }),
    )
    const { getCatalog } = useHelperCompletion({ fetch })

    const first = getCatalog()
    const second = getCatalog()

    resolveFetch(sampleCatalog)
    const [a, b] = await Promise.all([first, second])

    expect(fetch).toHaveBeenCalledTimes(1)
    expect(a).toBe(sampleCatalog)
    expect(b).toBe(sampleCatalog)
  })

  it('resolves to an empty array when the fetch fails', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const fetch = vi.fn().mockRejectedValue(new Error('boom'))
    const { getCatalog } = useHelperCompletion({ fetch })

    const result = await getCatalog()

    expect(result).toEqual([])
    expect(warnSpy).toHaveBeenCalled()
  })

  it('normalises non-array results to an empty array', async () => {
    const fetch = vi.fn().mockResolvedValue({ not: 'an array' })
    const { getCatalog } = useHelperCompletion({ fetch })

    const result = await getCatalog()

    expect(result).toEqual([])
  })
})
