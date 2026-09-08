import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const changeHandlers: Array<() => void> = []
const blurHandlers: Array<() => void> = []

const sharedModel = { dispose: vi.fn() }

const editorInstance = {
  getValue: vi.fn(() => 'current'),
  setValue: vi.fn(),
  updateOptions: vi.fn(),
  focus: vi.fn(),
  dispose: vi.fn(),
  getModel: vi.fn(() => sharedModel),
  onDidChangeModelContent: vi.fn((cb: () => void) => changeHandlers.push(cb)),
  onDidBlurEditorText: vi.fn((cb: () => void) => blurHandlers.push(cb)),
  revealLineInCenter: vi.fn(),
  setPosition: vi.fn(),
}

const create = vi.fn(() => editorInstance)
const setModelLanguage = vi.fn()
const setModelMarkers = vi.fn()
const registerCompletionItemProvider = vi.fn(() => ({ dispose: vi.fn() }))

vi.mock('monaco-editor', () => ({
  editor: {
    create: (...args: unknown[]) => create(...args),
    setModelLanguage: (...args: unknown[]) => setModelLanguage(...args),
    setModelMarkers: (...args: unknown[]) => setModelMarkers(...args),
  },
  MarkerSeverity: { Error: 8, Warning: 4 },
  languages: {
    CompletionItemKind: { Method: 1 },
    registerCompletionItemProvider,
  },
}))

import MonacoEditor from '../MonacoEditor.vue'

const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

describe('MonacoEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    changeHandlers.length = 0
    blurHandlers.length = 0
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('creates a Monaco editor with line numbers enabled', async () => {
    mount(MonacoEditor, { props: { modelValue: 'hello', language: 'java' } })
    await flush()

    expect(create).toHaveBeenCalledTimes(1)
    const options = create.mock.calls[0][1] as Record<string, unknown>
    expect(options.lineNumbers).toBe('on')
    expect(options.value).toBe('hello')
    expect(options.language).toBe('java')
  })

  it('emits update:modelValue when the editor content changes', async () => {
    const wrapper = mount(MonacoEditor, { props: { modelValue: 'old' } })
    await flush()

    editorInstance.getValue.mockReturnValueOnce('new')
    for (const cb of changeHandlers) cb()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['new'])
  })

  it('emits blur when the editor loses focus', async () => {
    const wrapper = mount(MonacoEditor, { props: { modelValue: 'x' } })
    await flush()

    for (const cb of blurHandlers) cb()

    expect(wrapper.emitted('blur')).toHaveLength(1)
  })

  it('pushes external modelValue changes into the editor', async () => {
    const wrapper = mount(MonacoEditor, { props: { modelValue: 'a' } })
    await flush()

    editorInstance.getValue.mockReturnValue('a')
    await wrapper.setProps({ modelValue: 'b' })

    expect(editorInstance.setValue).toHaveBeenCalledWith('b')
  })

  it('disposes the editor on unmount', async () => {
    const wrapper = mount(MonacoEditor, { props: { modelValue: 'a' } })
    await flush()

    wrapper.unmount()

    expect(editorInstance.dispose).toHaveBeenCalled()
  })

  it('does not register a completion provider when helperCatalogFetch is omitted', async () => {
    mount(MonacoEditor, { props: { modelValue: 'a' } })
    await flush()

    expect(registerCompletionItemProvider).not.toHaveBeenCalled()
  })

  it('registers a completion provider when helperCatalogFetch is provided', async () => {
    const wrapper = mount(MonacoEditor, {
      props: {
        modelValue: 'a',
        helperCatalogFetch: vi.fn().mockResolvedValue([]),
      },
    })
    await flush()

    expect(registerCompletionItemProvider).toHaveBeenCalledTimes(1)
    const call = registerCompletionItemProvider.mock.calls[0] as unknown as [
      string,
      { triggerCharacters: string[] },
    ]
    expect(call[0]).toBe('java')
    expect(call[1].triggerCharacters).toEqual(['"'])

    wrapper.unmount()
  })

  it('maps markers to setModelMarkers with owner "dsl" on mount and on prop change', async () => {
    const wrapper = mount(MonacoEditor, {
      props: {
        modelValue: 'x',
        markers: [
          { line: 4, column: 2, message: 'boom', severity: 'error' },
          { line: null, column: null, message: 'no-pos', severity: 'warning' },
        ],
      },
    })
    await flush()

    // initial apply
    expect(setModelMarkers).toHaveBeenCalled()
    const [modelArg, ownerArg, markersArg] = setModelMarkers.mock.calls.at(-1) as [
      unknown,
      string,
      Array<Record<string, unknown>>,
    ]
    expect(modelArg).toBe(editorInstance.getModel())
    expect(ownerArg).toBe('dsl')
    expect(markersArg).toHaveLength(2)
    expect(markersArg[0]).toMatchObject({
      severity: 8,
      message: 'boom',
      startLineNumber: 4,
      startColumn: 2,
      endLineNumber: 4,
      endColumn: 3,
    })
    expect(markersArg[1]).toMatchObject({
      severity: 4,
      message: 'no-pos',
      startLineNumber: 1,
      startColumn: 1,
      endLineNumber: 1,
      endColumn: 2,
    })

    // update: empty array clears markers (same owner, same model)
    await wrapper.setProps({ markers: [] })
    const lastCall = setModelMarkers.mock.calls.at(-1) as [unknown, string, unknown[]]
    expect(lastCall[0]).toBe(modelArg)
    expect(lastCall[1]).toBe('dsl')
    expect(lastCall[2]).toEqual([])
  })

  it('revealPosition centers the line, sets the cursor, and focuses', async () => {
    const wrapper = mount(MonacoEditor, { props: { modelValue: 'x' } })
    await flush()

    const editor = wrapper.vm as unknown as {
      revealPosition: (line: number, column?: number) => void
    }
    editor.revealPosition(3, 5)

    expect(editorInstance.revealLineInCenter).toHaveBeenCalledWith(3)
    expect(editorInstance.setPosition).toHaveBeenCalledWith({ lineNumber: 3, column: 5 })
    expect(editorInstance.focus).toHaveBeenCalled()
  })

  it('revealPosition is a no-op for invalid line numbers', async () => {
    const wrapper = mount(MonacoEditor, { props: { modelValue: 'x' } })
    await flush()

    const editor = wrapper.vm as unknown as {
      revealPosition: (line: number, column?: number) => void
    }
    editor.revealPosition(0)
    editor.revealPosition(-1)
    editor.revealPosition(Number.NaN)

    expect(editorInstance.revealLineInCenter).not.toHaveBeenCalled()
    expect(editorInstance.setPosition).not.toHaveBeenCalled()
    expect(editorInstance.focus).not.toHaveBeenCalled()
  })
})
