import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { consumeRunAgain, stashRunAgain } from '../runAgainHandoff'

const KEY = 'cbs.nova.run-again'

describe('runAgainHandoff', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
  })

  afterEach(() => {
    window.sessionStorage.clear()
  })

  it('round-trips: stashRunAgain followed by consumeRunAgain returns the same input', () => {
    const input = { name: 'Alice', age: 30, nested: { tags: ['a', 'b'] } }
    stashRunAgain('c1', input)

    expect(window.sessionStorage.getItem(KEY)).toBe(JSON.stringify({ name: 'c1', input }))
    expect(consumeRunAgain('c1')).toEqual(input)
  })

  it('consuming a matched stash removes the key (one-shot handoff)', () => {
    stashRunAgain('c1', { foo: 'bar' })
    expect(consumeRunAgain('c1')).toEqual({ foo: 'bar' })
    expect(window.sessionStorage.getItem(KEY)).toBeNull()
  })

  it('discards a stash when the name mismatches and returns null', () => {
    stashRunAgain('other', { foo: 'bar' })

    expect(consumeRunAgain('c1')).toBeNull()

    // mismatch = stale stash: the key must still be removed so it can't
    // poison a later visit to the runner for the wrong definition.
    expect(window.sessionStorage.getItem(KEY)).toBeNull()
  })

  it('returns null when there is no stash', () => {
    expect(consumeRunAgain('c1')).toBeNull()
  })

  it('returns null and clears the key when the stash is malformed JSON', () => {
    window.sessionStorage.setItem(KEY, '{not valid json')

    expect(consumeRunAgain('c1')).toBeNull()
    expect(window.sessionStorage.getItem(KEY)).toBeNull()
  })
})