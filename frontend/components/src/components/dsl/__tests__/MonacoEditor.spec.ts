import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const changeHandlers: Array<() => void> = []
const blurHandlers: Array<() => void> = []

const editorInstance = {
  getValue: vi.fn(() => 'current'),
  setValue: vi.fn(),
  updateOptions: vi.fn(),
  focus: vi.fn(),
  dispose: vi.fn(),
  getModel: vi.fn(() => ({ dispose: vi.fn() })),
  onDidChangeModelContent: vi.fn((cb: () => void) => changeHandlers.push(cb)),
  onDidBlurEditorText: vi.fn((cb: () => void) => blurHandlers.push(cb)),
}

const create = vi.fn(() => editorInstance)
const setModelLanguage = vi.fn()

vi.mock('monaco-editor', () => ({
  editor: {
    create: (...args: unknown[]) => create(...args),
    setModelLanguage: (...args: unknown[]) => setModelLanguage(...args),
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
})
