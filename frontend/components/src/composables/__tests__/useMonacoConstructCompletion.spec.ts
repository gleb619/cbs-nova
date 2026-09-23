import type * as Monaco from 'monaco-editor'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DslConstruct } from '../../types/dsl'
import {
  buildConstructCompletionItems,
  useMonacoConstructCompletion,
} from '../useMonacoConstructCompletion'
import { buildHelperCompletionItems } from '../useMonacoHelperCompletion'

// Fixture constructs payload — mirrors DslConstruct rows from the workbench
// store (loaded via GET /api/v1/dsl/definitions).
const orderSaga: DslConstruct = {
  name: 'OrderSaga',
  type: 'Process',
  status: 'Published',
  taskQueue: 'order-saga',
  inputType: 'OrderIn',
  outputType: 'OrderOut',
  description: 'Orchestrates the order flow',
}
const kycCheck: DslConstruct = {
  name: 'KYC_CHECK',
  type: 'Transaction',
  status: 'Published',
  inputType: 'KycIn',
  outputType: 'KycOut',
}
const greetFn: DslConstruct = {
  name: 'greetFn',
  type: 'Function',
  status: 'Published',
  outputType: 'GreetingOut',
}
const riskAssessment: DslConstruct = {
  name: 'riskAssessment',
  type: 'Helper',
  status: 'Published',
  description: 'Assesses customer risk level',
}

const sampleConstructs: DslConstruct[] = [orderSaga, kycCheck, greetFn, riskAssessment]

const wordRange: Monaco.IRange = {
  startLineNumber: 1,
  startColumn: 1,
  endLineNumber: 1,
  endColumn: 1,
}

describe('buildConstructCompletionItems', () => {
  it('includes one snippet scaffold per DSL construct type', () => {
    const items = buildConstructCompletionItems({ wordRange, constructs: [] })

    const scaffolds = items.filter((i) => i.kind === 28 /* Snippet */)
    expect(scaffolds.map((i) => i.label)).toEqual([
      'Dsl.process',
      'Dsl.transaction',
      'Dsl.function',
    ])
    for (const scaffold of scaffolds) {
      expect(scaffold.insertText).toContain('${1:name}')
      expect(scaffold.insertTextRules).toBe(4 /* InsertAsSnippet */)
      expect(scaffold.range).toBe(wordRange)
    }
  })

  it('embeds the builder chain in the process scaffold', () => {
    const items = buildConstructCompletionItems({ wordRange, constructs: [] })
    const process = items.find((i) => i.label === 'Dsl.process')

    expect(process?.insertText).toContain('Dsl.process("${1:name}")')
    expect(process?.insertText).toContain('.taskQueue("${2:task-queue}")')
    expect(process?.insertText).toContain('.input(${4:InputType}.class)')
    expect(process?.insertText).toContain('.execute(ctx -> {')
    expect(process?.insertText).toContain('.build();')
  })

  it('derives one item per non-helper construct with its name as label', () => {
    const items = buildConstructCompletionItems({ wordRange, constructs: sampleConstructs })

    const derived = items.filter((i) => i.kind === 5 /* Class */ || i.kind === 1 /* Function */)
    expect(derived.map((i) => i.label)).toEqual(['OrderSaga', 'KYC_CHECK', 'greetFn'])
  })

  it('formats the detail as "Type · InputType → OutputType" when types are present', () => {
    const items = buildConstructCompletionItems({ wordRange, constructs: sampleConstructs })
    const orderSagaItem = items.find((i) => i.label === 'OrderSaga')

    expect(orderSagaItem?.kind).toBe(5 /* Class */)
    expect(orderSagaItem?.detail).toBe('Process · OrderIn → OrderOut')
    expect(orderSagaItem?.documentation).toBe('Orchestrates the order flow')
    expect(orderSagaItem?.insertText).toBe('OrderSaga')
  })

  it('maps functions to the Function kind and falls back to "?" / "void" for missing types', () => {
    const items = buildConstructCompletionItems({ wordRange, constructs: sampleConstructs })
    const fn = items.find((i) => i.label === 'greetFn')

    expect(fn?.kind).toBe(1 /* Function */)
    expect(fn?.detail).toBe('Function · ? → GreetingOut')
  })

  it('skips Helper constructs — they are already surfaced by helper completion', () => {
    const items = buildConstructCompletionItems({ wordRange, constructs: sampleConstructs })

    expect(items.some((i) => i.label === 'riskAssessment')).toBe(false)
  })

  it('includes stage-builder keyword items with snippet placeholders', () => {
    const items = buildConstructCompletionItems({ wordRange, constructs: [] })

    const taskQueue = items.find((i) => i.label === '.taskQueue')
    expect(taskQueue?.insertText).toBe('.taskQueue("${1:task-queue}")')
    expect(taskQueue?.insertTextRules).toBe(4 /* InsertAsSnippet */)

    const compensation = items.find((i) => i.label === '.compensation')
    expect(compensation).toBeDefined()
    expect(items.some((i) => i.label === '.execute')).toBe(true)
    expect(items.some((i) => i.label === '.build')).toBe(true)
  })

  it('produces no label overlap with helper completion items', () => {
    const helperItems = buildHelperCompletionItems({
      wordRange,
      catalog: [{ name: 'uuidV7' }, { name: 'base64' }, { name: 'httpCall' }],
    })
    const constructItems = buildConstructCompletionItems({
      wordRange,
      constructs: sampleConstructs,
    })

    const helperLabels = new Set(helperItems.map((i) => i.label))
    const overlapping = constructItems.filter((i) => helperLabels.has(String(i.label)))

    expect(overlapping).toEqual([])
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
      CompletionItemKind: { Method: 0 } as unknown as typeof Monaco.languages.CompletionItemKind,
      registerCompletionItemProvider: registerSpy,
    },
  } as unknown as typeof Monaco

  function triggerProvider(model: unknown, position: unknown) {
    if (!stored) throw new Error('no provider registered')
    return stored.provideCompletionItems(model, position) as Promise<unknown>
  }

  return { monaco, registerSpy, disposeSpy, triggerProvider }
}

describe('useMonacoConstructCompletion', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('registers exactly one CompletionItemProvider per monaco instance regardless of subscribers', async () => {
    const fake = createFakeMonaco()
    const getConstructs = vi.fn().mockResolvedValue(sampleConstructs)

    const release1 = useMonacoConstructCompletion({ monaco: fake.monaco, getConstructs })
    const release2 = useMonacoConstructCompletion({ monaco: fake.monaco, getConstructs })
    const release3 = useMonacoConstructCompletion({ monaco: fake.monaco, getConstructs })

    expect(fake.registerSpy).toHaveBeenCalledTimes(1)

    const modelish = { getWordUntilPosition: () => ({ startColumn: 1, endColumn: 1 }) }
    const posish = { lineNumber: 1 }
    const result = await (
      fake.triggerProvider as unknown as (
        m: unknown,
        p: unknown,
      ) => Promise<{ suggestions: unknown }>
    )(modelish, posish)

    expect(getConstructs).toHaveBeenCalled()
    expect(result.suggestions).toHaveLength(
      buildConstructCompletionItems({ wordRange, constructs: sampleConstructs }).length,
    )

    release1()
    release2()
    release3()
  })

  it('disposes the provider only when the last subscriber releases', () => {
    const fake = createFakeMonaco()
    const getConstructs = vi.fn().mockResolvedValue([])

    const release1 = useMonacoConstructCompletion({ monaco: fake.monaco, getConstructs })
    const release2 = useMonacoConstructCompletion({ monaco: fake.monaco, getConstructs })
    const release3 = useMonacoConstructCompletion({ monaco: fake.monaco, getConstructs })

    release1()
    release2()
    expect(fake.disposeSpy).not.toHaveBeenCalled()

    release3()
    expect(fake.disposeSpy).toHaveBeenCalledTimes(1)
  })

  it('forwards the resolved constructs payload to the suggestion list', async () => {
    const fake = createFakeMonaco()
    const getConstructs = vi.fn().mockResolvedValue(sampleConstructs)
    const release = useMonacoConstructCompletion({ monaco: fake.monaco, getConstructs })

    const suggestions = await (
      fake.triggerProvider as unknown as (
        m: unknown,
        p: unknown,
      ) => Promise<{ suggestions: { label: string }[] }>
    )({ getWordUntilPosition: () => ({ startColumn: 1, endColumn: 1 }) }, { lineNumber: 1 })

    const labels = suggestions.suggestions.map((s) => s.label)
    expect(labels).toContain('OrderSaga')
    expect(labels).toContain('KYC_CHECK')
    expect(labels).toContain('greetFn')
    expect(labels).not.toContain('riskAssessment')

    release()
  })

  it('uses the triggerCharacters from the registered provider', () => {
    const fake = createFakeMonaco()
    const release = useMonacoConstructCompletion({
      monaco: fake.monaco,
      getConstructs: vi.fn().mockResolvedValue([]),
    })

    const args = fake.registerSpy.mock.calls[0] as unknown as [
      string,
      { triggerCharacters?: string[] },
    ]
    expect(args[1]?.triggerCharacters).toEqual(['.', ' '])

    release()
  })
})
