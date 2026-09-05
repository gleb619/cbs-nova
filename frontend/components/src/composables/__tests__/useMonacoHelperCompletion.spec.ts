import type * as Monaco from 'monaco-editor'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildHelperCompletionItems, useMonacoHelperCompletion } from '../useMonacoHelperCompletion'

const uuidV7 = {
  name: 'uuidV7',
  description: 'Time-ordered UUID',
  inputType: 'UuidV7In',
  outputType: 'UuidV7Out',
  hasSideEffects: false,
}
const base64 = { name: 'base64', description: 'base64 codec', hasSideEffects: false }
const httpCall = {
  name: 'httpCall',
  hasSideEffects: true,
  previewBehavior: 'recorded in preview',
}

const sampleCatalog = [uuidV7, base64, httpCall]

const wordRange: Monaco.IRange = {
  startLineNumber: 1,
  startColumn: 1,
  endLineNumber: 1,
  endColumn: 1,
}

describe('buildHelperCompletionItems', () => {
  it('produces one CompletionItem per catalog entry with the helper name as label', () => {
    const items = buildHelperCompletionItems({ wordRange, catalog: sampleCatalog })

    expect(items).toHaveLength(3)
    expect(items.map((i) => i.label)).toEqual(['uuidV7', 'base64', 'httpCall'])
    expect(items.every((i) => i.kind === 1 /* Method */)).toBe(true)
    expect(items.every((i) => i.insertText === i.label)).toBe(true)
    expect(items.every((i) => i.range === wordRange)).toBe(true)
  })

  it('formats the detail as "InputType → OutputType" when both are present', () => {
    const [item] = buildHelperCompletionItems({
      wordRange,
      catalog: [uuidV7],
    })

    expect(item.detail).toBe('UuidV7In → UuidV7Out')
  })

  it('falls back to "?" input and "void" output when output type is missing', () => {
    const [item] = buildHelperCompletionItems({
      wordRange,
      catalog: [{ name: 'noop', inputType: 'SomeInput', hasSideEffects: false }],
    })

    expect(item.detail).toBe('SomeInput → void')
  })

  it('uses previewBehavior as detail when types are absent and previewBehavior present', () => {
    const [item] = buildHelperCompletionItems({
      wordRange,
      catalog: [httpCall],
    })

    expect(item.detail).toBe('recorded in preview')
  })

  it('falls back to an empty detail when neither types nor previewBehavior are present', () => {
    const [item] = buildHelperCompletionItems({
      wordRange,
      catalog: [{ name: 'noop', hasSideEffects: false }],
    })

    expect(item.detail).toBe('')
  })

  it('exposes the entry description as documentation', () => {
    const [item] = buildHelperCompletionItems({
      wordRange,
      catalog: [base64],
    })

    expect(item.documentation).toBe('base64 codec')
  })
})

interface FakeMonacoOptions {
  onRegister?: (provider: {
    provideCompletionItems: (model: unknown, position: unknown) => unknown
  }) => void
}

function createFakeMonaco(options: FakeMonacoOptions = {}): {
  monaco: typeof Monaco
  registerSpy: ReturnType<typeof vi.fn>
  disposeSpy: ReturnType<typeof vi.fn>
  triggerProvider: (model: unknown, position: unknown) => Promise<unknown>
} {
  let stored: {
    provideCompletionItems: (model: unknown, position: unknown) => unknown
  } | null = null

  const registerSpy = vi.fn(
    (_language: string, provider: { provideCompletionItems: (...args: unknown[]) => unknown }) => {
      stored = provider
      options.onRegister?.(provider)
      return { dispose: disposeSpy }
    },
  )
  const disposeSpy = vi.fn(() => {
    stored = null
  })

  const monaco = {
    languages: {
      CompletionItemKind: { Method: 1 } as unknown as typeof Monaco.languages.CompletionItemKind,
      registerCompletionItemProvider: registerSpy,
    },
  } as unknown as typeof Monaco

  function triggerProvider(model: unknown, position: unknown) {
    if (!stored) throw new Error('no provider registered')
    return stored.provideCompletionItems(model, position)
  }

  return { monaco, registerSpy, disposeSpy, triggerProvider }
}

describe('useMonacoHelperCompletion', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('registers exactly one CompletionItemProvider per monaco instance regardless of subscribers', async () => {
    const fake = createFakeMonaco()
    const getCatalog = vi.fn().mockResolvedValue(sampleCatalog)

    const release1 = useMonacoHelperCompletion({ monaco: fake.monaco, getCatalog })
    const release2 = useMonacoHelperCompletion({ monaco: fake.monaco, getCatalog })
    const release3 = useMonacoHelperCompletion({ monaco: fake.monaco, getCatalog })

    expect(fake.registerSpy).toHaveBeenCalledTimes(1)

    const modelish = { getWordUntilPosition: () => ({ startColumn: 1, endColumn: 1 }) }
    const posish = { lineNumber: 1 }
    const result = await (
      fake.triggerProvider as unknown as (
        m: unknown,
        p: unknown,
      ) => Promise<{ suggestions: unknown }>
    )(modelish, posish)

    expect(getCatalog).toHaveBeenCalled()
    expect(result.suggestions).toHaveLength(sampleCatalog.length)

    release1()
    release2()
    release3()
  })

  it('disposes the provider only when the last subscriber releases', () => {
    const fake = createFakeMonaco()
    const getCatalog = vi.fn().mockResolvedValue([])

    const release1 = useMonacoHelperCompletion({ monaco: fake.monaco, getCatalog })
    const release2 = useMonacoHelperCompletion({ monaco: fake.monaco, getCatalog })
    const release3 = useMonacoHelperCompletion({ monaco: fake.monaco, getCatalog })

    release1()
    release2()
    expect(fake.disposeSpy).not.toHaveBeenCalled()

    release3()
    expect(fake.disposeSpy).toHaveBeenCalledTimes(1)
  })

  it('forwards the resolved catalog to buildHelperCompletionItems inside the provider', async () => {
    const fake = createFakeMonaco()
    const getCatalog = vi.fn().mockResolvedValue(sampleCatalog)
    const release = useMonacoHelperCompletion({ monaco: fake.monaco, getCatalog })

    const suggestions = await (
      fake.triggerProvider as unknown as (
        m: unknown,
        p: unknown,
      ) => Promise<{ suggestions: { label: string }[] }>
    )({ getWordUntilPosition: () => ({ startColumn: 1, endColumn: 1 }) }, { lineNumber: 1 })

    expect(suggestions.suggestions.map((s) => s.label)).toEqual(['uuidV7', 'base64', 'httpCall'])

    release()
  })

  it('passes an empty suggestion list when the catalog resolves to []', async () => {
    const fake = createFakeMonaco()
    const getCatalog = vi.fn().mockResolvedValue([])
    const release = useMonacoHelperCompletion({ monaco: fake.monaco, getCatalog })

    const suggestions = await (
      fake.triggerProvider as unknown as (
        m: unknown,
        p: unknown,
      ) => Promise<{ suggestions: unknown[] }>
    )({ getWordUntilPosition: () => ({ startColumn: 1, endColumn: 1 }) }, { lineNumber: 1 })

    expect(suggestions.suggestions).toEqual([])

    release()
  })

  it('uses the triggerCharacters from the registered provider', () => {
    const fake = createFakeMonaco()
    const release = useMonacoHelperCompletion({
      monaco: fake.monaco,
      getCatalog: vi.fn().mockResolvedValue([]),
    })

    const args = fake.registerSpy.mock.calls[0] as unknown as [
      string,
      { triggerCharacters?: string[] },
    ]
    expect(args[1]?.triggerCharacters).toEqual(['"'])

    release()
  })
})
