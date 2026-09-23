import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  __resetConstructSchemaCache,
  DSL_SCHEMA_FETCH_KEY,
} from '../../composables/useConstructSchema'
import type { DslConstruct } from '../../types/dsl'
import BodyEditor from '../dsl/BodyEditor.vue'
import CodeTab from '../dsl/CodeTab.vue'
import StructureTab from '../dsl/StructureTab.vue'

vi.mock('../dsl/MonacoEditor.vue', () => ({
  default: {
    name: 'MonacoEditorStub',
    props: {
      modelValue: { type: String, default: '' },
      language: { type: String, default: 'java' },
      readOnly: { type: Boolean, default: false },
      placeholder: { type: String, default: '' },
    },
    emits: ['update:modelValue', 'blur'],
    template: `<textarea
      data-testid="code-tab-textarea"
      :readonly="readOnly"
      :placeholder="placeholder"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
      @blur="$emit('blur')"
    />`,
  },
}))

const construct: DslConstruct = {
  name: 'CreateOrder',
  type: 'Process',
  status: 'Valid',
}

import ExplainTab from '../dsl/ExplainTab.vue'
import PreviewTab from '../dsl/PreviewTab.vue'
import RunResultPanel from '../dsl/RunResultPanel.vue'

const defaultFetchMock = vi.fn().mockResolvedValue({})

function mountBodyEditor(props: Record<string, unknown>, fetchMock = defaultFetchMock) {
  // BodyEditor references StructureTab / CodeTab in its template without importing
  // them (they are Nuxt auto-imported in the host app). Register real children here
  // so they resolve under vitest, mirroring how OutputPanel registers its children.
  return mount(BodyEditor, {
    props: props as never,
    global: {
      components: {
        StructureTab,
        CodeTab,
        PreviewTab,
        RunResultPanel,
        ExplainTab,
      },
      provide: { [DSL_SCHEMA_FETCH_KEY as symbol]: fetchMock },
    },
  })
}

describe('BodyEditor', () => {
  beforeEach(() => {
    localStorage.clear()
    __resetConstructSchemaCache()
  })
  it('exposes root data-testid', () => {
    const wrapper = mountBodyEditor({ construct })

    expect(wrapper.find('[data-testid="body-editor"]').exists()).toBe(true)
  })

  it('renders the Structure and Code tab buttons with Structure active by default', () => {
    const wrapper = mountBodyEditor({ construct })

    const buttons = wrapper.findAll('button').map((b) => b.text())
    expect(buttons).toContain('Structure')
    expect(buttons).toContain('Code')

    const structureButton = wrapper.findAll('button').find((b) => b.text() === 'Structure')!
    const codeButton = wrapper.findAll('button').find((b) => b.text() === 'Code')!
    expect(structureButton.classes()).toContain('border-blue-500')
    expect(codeButton.classes()).not.toContain('border-blue-500')
  })

  it('switches the active tab to Code when the Code tab is clicked', async () => {
    const wrapper = mountBodyEditor({ construct })

    const codeButton = wrapper.findAll('button').find((b) => b.text() === 'Code')!
    await codeButton.trigger('click')

    const structureButton = wrapper.findAll('button').find((b) => b.text() === 'Structure')!
    expect(structureButton.classes()).not.toContain('border-blue-500')
    expect(codeButton.classes()).toContain('border-blue-500')
    expect(wrapper.find('textarea').exists()).toBe(true)
  })

  it('marks the code editor read-only when no construct is selected', async () => {
    const wrapper = mountBodyEditor({ construct: null, code: 'foo' })

    const codeButton = wrapper.findAll('button').find((b) => b.text() === 'Code')!
    await codeButton.trigger('click')

    const textarea = wrapper.find('textarea')
    expect(textarea.attributes('readonly')).toBeDefined()
    expect(textarea.attributes('placeholder')).toBe('No code available')
  })

  it('leaves the code editor writable when a construct is selected', async () => {
    const wrapper = mountBodyEditor({ construct, code: 'foo' })

    const codeButton = wrapper.findAll('button').find((b) => b.text() === 'Code')!
    await codeButton.trigger('click')

    const textarea = wrapper.find('textarea')
    expect(textarea.attributes('readonly')).toBeUndefined()
    expect(textarea.attributes('placeholder')).toBe('Write DSL here...')
  })

  it('emits update:code when controlled code is edited', async () => {
    const wrapper = mountBodyEditor({ construct, code: 'initial' })

    const codeButton = wrapper.findAll('button').find((b) => b.text() === 'Code')!
    await codeButton.trigger('click')

    await wrapper.find('textarea').setValue('edited body')

    expect(wrapper.emitted('update:code')).toBeTruthy()
    expect(wrapper.emitted('update:code')!.at(-1)).toEqual(['edited body'])
  })

  it('renders Preview and Explain tab buttons when callbacks are provided', () => {
    const wrapper = mountBodyEditor({
      construct,
      preview: vi.fn(),
      explain: vi.fn(),
    })

    const buttons = wrapper.findAll('button').map((b) => b.text())
    expect(buttons).toContain('Preview')
    expect(buttons).toContain('Explain')
  })

  it('calls the preview callback and displays the result', async () => {
    const preview = vi.fn().mockResolvedValue({ result: { ok: true } })
    const wrapper = mountBodyEditor({ construct, preview })

    const previewButton = wrapper.findAll('button').find((b) => b.text() === 'Preview')!
    await previewButton.trigger('click')
    await flushPromises()

    const runButton = wrapper.findAll('button').find((b) => b.text() === 'Run')!
    await runButton.trigger('click')
    await flushPromises()

    expect(preview).toHaveBeenCalledWith('CreateOrder', {}, { startedFrom: 'workbench' })
    expect(wrapper.text()).toContain('"ok": true')
  })

  it('calls the explain callback and displays the description', async () => {
    const explain = vi.fn().mockResolvedValue({
      description: 'Test flow',
      markdown: 'graph TD',
    })
    const wrapper = mountBodyEditor({ construct, explain })

    const explainButton = wrapper.findAll('button').find((b) => b.text() === 'Explain')!
    await explainButton.trigger('click')
    await flushPromises()

    const runButton = wrapper.findAll('button').find((b) => b.text() === 'Run')!
    await runButton.trigger('click')
    await flushPromises()

    expect(explain).toHaveBeenCalledWith('CreateOrder', {}, { startedFrom: 'workbench' })
    expect(wrapper.text()).toContain('Test flow')
  })

  it('pulls in externally changed controlled code via keyed remount when the construct changes', async () => {
    const wrapper = mountBodyEditor({
      construct: { name: 'ConstructA', type: 'Process', status: 'Valid' },
      code: 'aaa',
    })

    const codeButton = wrapper.findAll('button').find((b) => b.text() === 'Code')!
    await codeButton.trigger('click')
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('aaa')

    await wrapper.setProps({
      construct: { name: 'ConstructB', type: 'Process', status: 'Valid' },
      code: 'bbb',
    })

    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('bbb')
  })
})
