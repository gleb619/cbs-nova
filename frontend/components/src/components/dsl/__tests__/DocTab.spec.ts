import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DocTab from '../DocTab.vue'

vi.mock('../MonacoEditor.vue', () => ({
  default: {
    name: 'MonacoEditorStub',
    props: {
      modelValue: { type: String, default: '' },
      language: { type: String, default: 'java' },
      readOnly: { type: Boolean, default: false },
      placeholder: { type: String, default: '' },
    },
    emits: ['update:modelValue'],
    template: `<textarea
      data-testid="doc-tab-textarea"
      :readonly="readOnly"
      :placeholder="placeholder"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
    />`,
  },
}))

const SAMPLE_DOC =
  '---\nname: BatchProcessing\ndescription: Sums values\n---\n\n# BatchProcessing\n\nSome text\n'

const mountTab = (props: Record<string, unknown> = {}) =>
  mount(DocTab, { props: { doc: SAMPLE_DOC, ...props } })

describe('DocTab', () => {
  let wrapper: ReturnType<typeof mountTab>

  beforeEach(() => {
    window.localStorage.clear()
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  it('starts in form mode and renders frontmatter fields plus body', () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="doc-tab-form"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="doc-tab-raw"]').exists()).toBe(false)
    expect(
      (wrapper.find('[data-testid="schema-field-name"]').element as HTMLInputElement).value,
    ).toBe('BatchProcessing')
    expect(
      (wrapper.find('[data-testid="schema-field-description"]').element as HTMLInputElement).value,
    ).toBe('Sums values')
    expect(
      (wrapper.find('[data-testid="doc-tab-body"]').element as HTMLTextAreaElement).value,
    ).toBe('# BatchProcessing\n\nSome text\n')
  })

  it('emits update:doc with serialized frontmatter when a field changes', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="schema-field-name"]').setValue('NewName')

    const emitted = wrapper.emitted('update:doc')?.at(-1)?.[0] as string
    expect(emitted).toContain('name: NewName')
    expect(emitted).toContain('description: Sums values')
    expect(emitted).toContain('# BatchProcessing')
  })

  it('emits update:doc with frontmatter preserved when the body changes', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="doc-tab-body"]').setValue('# New body')

    expect(wrapper.emitted('update:doc')?.at(-1)?.[0]).toBe(
      '---\nname: BatchProcessing\ndescription: Sums values\n---\n\n# New body\n',
    )
  })

  it('adds a frontmatter block when editing a doc that has none', async () => {
    wrapper = mountTab({ doc: '# Title\n' })

    await wrapper.find('[data-testid="schema-field-name"]').setValue('MyDsl')

    expect(wrapper.emitted('update:doc')?.at(-1)?.[0]).toBe('---\nname: MyDsl\n---\n\n# Title\n')
  })

  it('switches to raw mode with a markdown editor and back', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="doc-tab-mode-raw"]').trigger('click')

    const raw = wrapper.find('[data-testid="doc-tab-raw"]')
    expect(raw.exists()).toBe(true)
    expect(wrapper.find('[data-testid="doc-tab-form"]').exists()).toBe(false)
    const textarea = wrapper.find('[data-testid="doc-tab-textarea"]')
    expect((textarea.element as HTMLTextAreaElement).value).toBe(SAMPLE_DOC)
    expect(textarea.attributes('placeholder')).toBe('Write documentation here...')

    await wrapper.find('[data-testid="doc-tab-mode-form"]').trigger('click')
    expect(wrapper.find('[data-testid="doc-tab-form"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="doc-tab-raw"]').exists()).toBe(false)
  })

  it('emits update:doc when typing in the raw editor', async () => {
    wrapper = mountTab()
    await wrapper.find('[data-testid="doc-tab-mode-raw"]').trigger('click')

    await wrapper.find('[data-testid="doc-tab-textarea"]').setValue('# raw edit\n')

    expect(wrapper.emitted('update:doc')?.at(-1)?.[0]).toBe('# raw edit\n')
  })

  it('parses raw edits when switching back to form mode', async () => {
    wrapper = mountTab()
    await wrapper.find('[data-testid="doc-tab-mode-raw"]').trigger('click')
    await wrapper
      .find('[data-testid="doc-tab-textarea"]')
      .setValue('---\nname: RawName\ndescription: from raw\n---\n\n# Body\n')

    await wrapper.find('[data-testid="doc-tab-mode-form"]').trigger('click')

    expect(
      (wrapper.find('[data-testid="schema-field-name"]').element as HTMLInputElement).value,
    ).toBe('RawName')
    expect(
      (wrapper.find('[data-testid="doc-tab-body"]').element as HTMLTextAreaElement).value,
    ).toBe('# Body\n')
  })

  it('persists the selected mode', async () => {
    wrapper = mountTab()
    await wrapper.find('[data-testid="doc-tab-mode-raw"]').trigger('click')
    wrapper.unmount()

    expect(window.localStorage.getItem('cbs-nova:doc-tab:mode')).toBe(JSON.stringify('raw'))

    wrapper = mountTab()
    expect(wrapper.find('[data-testid="doc-tab-raw"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="doc-tab-form"]').exists()).toBe(false)
  })

  it('shows the dirty indicator and enables Save only after editing', async () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="doc-tab-dirty-indicator"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="doc-tab-save"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-testid="schema-field-name"]').setValue('Changed')

    expect(wrapper.find('[data-testid="doc-tab-dirty-indicator"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="doc-tab-save"]').attributes('disabled')).toBeUndefined()
  })

  it('emits save with the current doc when Save is clicked', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="doc-tab-body"]').setValue('# changed\n')
    await wrapper.find('[data-testid="doc-tab-save"]').trigger('click')

    expect(wrapper.emitted('save')?.at(-1)?.[0]).toBe(
      '---\nname: BatchProcessing\ndescription: Sums values\n---\n\n# changed\n',
    )
    expect(wrapper.find('[data-testid="doc-tab-dirty-indicator"]').exists()).toBe(false)
  })

  it('hides the toolbar and marks fields read-only in readOnly mode', () => {
    wrapper = mountTab({ readOnly: true })

    expect(wrapper.find('[data-testid="doc-tab-toolbar"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="schema-field-name"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="doc-tab-body"]').attributes('readonly')).toBeDefined()
  })

  it('marks the raw editor read-only in readOnly mode', () => {
    window.localStorage.setItem('cbs-nova:doc-tab:mode', JSON.stringify('raw'))
    wrapper = mountTab({ readOnly: true })

    const textarea = wrapper.find('[data-testid="doc-tab-textarea"]')
    expect(textarea.attributes('readonly')).toBeDefined()
    expect(textarea.attributes('placeholder')).toBe('No documentation available')
  })

  it('renders the save status chip', async () => {
    wrapper = mountTab({ saveStatus: 'error' })

    const status = wrapper.find('[data-testid="doc-tab-save-status"]')
    expect(status.text()).toContain('Save failed')
    expect(status.classes()).toEqual(expect.arrayContaining(['bg-red-50']))

    await wrapper.find('[data-testid="doc-tab-save-retry"]').trigger('click')
    expect(wrapper.emitted('save')).toEqual([[SAMPLE_DOC]])
  })

  it('saves via the global dsl:save event when dirty', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="doc-tab-body"]').setValue('# changed\n')
    window.dispatchEvent(new Event('dsl:save'))

    expect(wrapper.emitted('save')?.at(-1)?.[0]).toContain('# changed')
  })
})
