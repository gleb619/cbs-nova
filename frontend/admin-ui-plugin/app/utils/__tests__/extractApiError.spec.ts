import { createError } from 'h3'
import { FetchError, createFetchError } from 'ofetch'
import { describe, expect, it } from 'vitest'
import { extractApiError } from '../extractApiError'

describe('extractApiError', () => {
  it('extracts the backend message and code from an h3 createError envelope', () => {
    const err = createError({
      statusCode: 400,
      statusMessage: 'Bad Request',
      data: { message: 'Unknown construct foo', code: 'DSL_NOT_FOUND' },
    })

    expect(extractApiError(err)).toEqual({
      message: 'Unknown construct foo',
      code: 'DSL_NOT_FOUND',
      status: 400,
    })
  })

  it('prefers data.message over the generic statusMessage', () => {
    const err = createError({
      statusCode: 500,
      statusMessage: 'Internal Server Error',
      data: { message: 'workflow exploded' },
    })

    expect(extractApiError(err).message).toBe('workflow exploded')
  })

  it('passes through nested data.diagnostics and data.details', () => {
    const diagnostics = [{ file: 'A.java', line: 3, message: 'oops' }]
    const details = { field: 'name' }
    const err = createError({
      statusCode: 422,
      statusMessage: 'Unprocessable Entity',
      data: { message: 'compile failed', code: 'COMPILE_ERROR', diagnostics, details },
    })

    const result = extractApiError(err)
    expect(result.diagnostics).toEqual(diagnostics)
    expect(result.details).toEqual(details)
  })

  it('extracts from an ofetch FetchError shape (response.status + data)', () => {
    const response = new Response('{}', { status: 502, statusText: 'Bad Gateway' })
    const data = { message: 'upstream blew up', code: 'UPSTREAM' }
    // ofetch stashes the parsed body on `response._data` at runtime.
    ;(response as unknown as { _data: unknown })._data = data
    const err = createFetchError({ request: '/api/v1/dsl/run/x', response } as any)

    const result = extractApiError(err)
    expect(result.message).toBe('upstream blew up')
    expect(result.code).toBe('UPSTREAM')
    expect(result.status).toBe(502)
  })

  it('handles a FetchError constructed without data', () => {
    const response = new Response('{}', { status: 503, statusText: 'Service Unavailable' })
    const err = createFetchError({ request: '/api/v1/dsl', response } as any)

    const result = extractApiError(err)
    expect(result.message).toBe('Service Unavailable')
    expect(result.status).toBe(503)
  })

  it('handles a plain Error', () => {
    expect(extractApiError(new Error('network down'))).toEqual({ message: 'network down' })
  })

  it('handles a string error', () => {
    expect(extractApiError('boom')).toEqual({ message: 'boom' })
  })

  it('handles undefined and null with the fallback', () => {
    expect(extractApiError(undefined)).toEqual({ message: 'Request failed' })
    expect(extractApiError(null)).toEqual({ message: 'Request failed' })
  })

  it('handles an object with none of the known fields', () => {
    expect(extractApiError({ weird: true })).toEqual({ message: 'Request failed' })
  })

  it('uses the caller-provided fallback', () => {
    expect(extractApiError(undefined, 'Failed to load')).toEqual({ message: 'Failed to load' })
  })

  it('resolves status from response.status when statusCode is absent', () => {
    // Minimal stand-in for an ofetch FetchError on a non-h3 client path:
    // no `statusCode`, status lives on `response.status`.
    const err = new FetchError('nope')
    Object.assign(err, { data: { message: 'backend msg' }, response: { status: 409 } })

    const result = extractApiError(err)
    expect(result.message).toBe('backend msg')
    expect(result.status).toBe(409)
  })

  it('falls back to statusMessage then message when data.message is missing', () => {
    const viaStatusMessage = createError({ statusCode: 403, statusMessage: 'Forbidden' })
    expect(extractApiError(viaStatusMessage).message).toBe('Forbidden')

    expect(extractApiError(new Error('plain failure')).message).toBe('plain failure')
  })

  it('never throws, even on hostile values', () => {
    const throwing = {
      get data() {
        throw new Error('nope')
      },
    }

    expect(extractApiError(throwing)).toEqual({ message: 'Request failed' })
  })
})
