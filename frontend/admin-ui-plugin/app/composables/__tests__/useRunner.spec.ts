import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../useDslApi', () => {
  const preview = vi.fn()
  const run = vi.fn()
  const explain = vi.fn()
  return {
    useDslApi: () => ({ preview, run, explain }),
  }
})

// eslint-disable-next-line import/first
import * as apiModule from '../useDslApi'
import { useRunner } from '../useRunner'

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

type ApiMock = {
  preview: ReturnType<typeof vi.fn>
  run: ReturnType<typeof vi.fn>
  explain: ReturnType<typeof vi.fn>
}

const getApiMocks = (): ApiMock => {
  const inst = (apiModule as unknown as { useDslApi: () => ApiMock }).useDslApi()
  return inst
}

describe('useRunner', () => {
  beforeEach(() => {
    getApiMocks().preview.mockReset()
    getApiMocks().run.mockReset()
    getApiMocks().explain.mockReset()
    // reset module-level singleton state
    const r = useRunner()
    r.selectDefinition(null)
    r.setMode('preview')
    r.resetOutput()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('selectDefinition null resets output to null and status to idle', () => {
    const r = useRunner()
    r.selectDefinition('foo')
    r.formData.value = { a: 1 }
    ;(r.status.value as string) = 'success'
    r.output.value = { result: 'old' } as never

    r.selectDefinition(null)

    expect(r.selectedDefinition.value).toBeNull()
    expect(r.output.value).toBeNull()
    expect(r.status.value).toBe('idle')
  })

  it('submit() with no selectedDefinition sets failed + NO_DEFINITION', async () => {
    const r = useRunner()
    r.selectDefinition(null)

    await r.submit()

    expect(r.status.value).toBe('failed')
    expect(r.output.value).toEqual({
      errors: [{ message: 'No definition selected', code: 'NO_DEFINITION' }],
    })
    expect(getApiMocks().preview).not.toHaveBeenCalled()
    expect(getApiMocks().run).not.toHaveBeenCalled()
    expect(getApiMocks().explain).not.toHaveBeenCalled()
  })

  it('preview path: status goes loading -> success with normalized output', async () => {
    const r = useRunner()
    r.selectDefinition('previewDef')
    r.setMode('preview')
    getApiMocks().preview.mockResolvedValueOnce({
      result: 'r1',
      metadata: { k: 'v' },
      description: 'desc',
      workflowId: 'wf-1',
    })

    const statusOrder: string[] = []
    const watcher = vi.fn(() => statusOrder.push(r.status.value as string))

    const p = r.submit().then(() => {
      watcher()
    })

    statusOrder.push(r.status.value as string)
    await p

    expect(getApiMocks().preview).toHaveBeenCalledWith('previewDef', r.formData.value)
    expect(r.output.value).toMatchObject({
      result: 'r1',
      metadata: { k: 'v' },
      description: 'desc',
      workflowId: 'wf-1',
    })
    expect(r.status.value).toBe('success')
    // Should have transitioned loading -> success
    expect(statusOrder).toContain('loading')
    expect(statusOrder[statusOrder.length - 1]).toBe('success')
  })

  it('run path: status goes running -> success with idempotency key header', async () => {
    const r = useRunner()
    r.selectDefinition('runDef')
    r.setMode('run')
    getApiMocks().run.mockResolvedValueOnce({ result: 'r2', workflowId: 'wf-2' })

    const before = r.status.value
    await r.submit()
    const after = r.status.value

    expect(before).toBe('idle')
    expect(getApiMocks().run).toHaveBeenCalledWith(
      'runDef',
      r.formData.value,
      undefined,
      { 'Idempotency-Key': expect.stringMatching(UUID_REGEX) },
    )
    expect(r.output.value).toMatchObject({ result: 'r2', workflowId: 'wf-2' })
    expect(after).toBe('success')
  })

  it('each run submission uses a different idempotency key', async () => {
    const r = useRunner()
    r.selectDefinition('runDef')
    r.setMode('run')
    getApiMocks().run.mockResolvedValue({ result: 'ok' })

    await r.submit()
    await r.submit()

    expect(getApiMocks().run).toHaveBeenCalledTimes(2)
    const key1 = (getApiMocks().run.mock.calls[0][3] as Record<string, string>)['Idempotency-Key']
    const key2 = (getApiMocks().run.mock.calls[1][3] as Record<string, string>)['Idempotency-Key']
    expect(key1).toMatch(UUID_REGEX)
    expect(key2).toMatch(UUID_REGEX)
    expect(key1).not.toBe(key2)
  })

  it('explain path calls api.explain', async () => {
    const r = useRunner()
    r.selectDefinition('expDef')
    r.setMode('explain')
    getApiMocks().explain.mockResolvedValueOnce({ description: 'explain me' })

    await r.submit()

    expect(getApiMocks().explain).toHaveBeenCalledWith('expDef', r.formData.value)
    expect(getApiMocks().preview).not.toHaveBeenCalled()
    expect(getApiMocks().run).not.toHaveBeenCalled()
    expect(r.output.value).toMatchObject({ description: 'explain me' })
    expect(r.status.value).toBe('success')
  })

  it('thrown error sets status failed and builds output via errorToOutput', async () => {
    const r = useRunner()
    r.selectDefinition('errDef')
    r.setMode('preview')
    getApiMocks().preview.mockRejectedValueOnce({
      data: { message: 'upstream blew', errors: [{ message: 'bad', code: 'BAD' }] },
      statusMessage: 'Bad Request',
    })

    await r.submit()

    expect(r.status.value).toBe('failed')
    expect(r.output.value).toEqual({
      errors: [{ message: 'bad', code: 'BAD' }],
    })
  })

  it('thrown error without data.errors falls back to a single REQUEST_FAILED error', async () => {
    const r = useRunner()
    r.selectDefinition('errDef2')
    r.setMode('preview')
    getApiMocks().preview.mockRejectedValueOnce(new Error('network down'))

    await r.submit()

    expect(r.status.value).toBe('failed')
    expect(r.output.value).toEqual({
      errors: [{ message: 'network down', code: 'REQUEST_FAILED' }],
    })
  })

  it('confirmRun in preview submits directly without modal', async () => {
    const r = useRunner()
    r.selectDefinition('preview2')
    r.setMode('preview')
    getApiMocks().preview.mockResolvedValueOnce({ result: 'ok' })

    await r.confirmRun()

    expect(r.showConfirmModal.value).toBe(false)
    expect(r.status.value).toBe('success')
    expect(getApiMocks().preview).toHaveBeenCalled()
  })

  it('confirmRun in run mode sets showConfirmModal and does not submit', async () => {
    const r = useRunner()
    r.selectDefinition('run2')
    r.setMode('run')
    r.formData.value = { x: 1 }

    await r.confirmRun()

    expect(r.showConfirmModal.value).toBe(true)
    expect(r.status.value).toBe('idle')
    expect(getApiMocks().run).not.toHaveBeenCalled()
  })
})
