import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CodeTab from '../CodeTab.vue'

vi.mock('../MonacoEditor.vue', () => ({
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

const mountTab = (props: Record<string, unknown> = {}) =>
  mount(CodeTab, { props: { code: 'initial', ...props } })

describe('CodeTab', () => {
  let wrapper: ReturnType<typeof mountTab>

  beforeEach(() => {
    window.localStorage.clear()
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.useRealTimers()
  })

  it('hides the toolbar when readOnly', () => {
    wrapper = mountTab({ readOnly: true })

    expect(wrapper.find('[data-testid="code-tab-toolbar"]').exists()).toBe(false)
  })

  it('shows the dirty indicator and enables Save only after editing', async () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="workbench-dirty-indicator"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="code-tab-save"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-testid="code-tab-textarea"]').setValue('changed')

    const indicator = wrapper.find('[data-testid="workbench-dirty-indicator"]')
    expect(indicator.exists()).toBe(true)
    expect(indicator.text()).toContain('unsaved changes')
    expect(wrapper.find('[data-testid="code-tab-save"]').attributes('disabled')).toBeUndefined()
  })

  it('emits save with the current code when Save is clicked', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="code-tab-textarea"]').setValue('changed')
    await wrapper.find('[data-testid="code-tab-save"]').trigger('click')

    expect(wrapper.emitted('save')).toEqual([['changed']])
    expect(wrapper.find('[data-testid="workbench-dirty-indicator"]').exists()).toBe(false)
  })

  it('autosaves on blur when the blur mode is picked', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="code-tab-autosave-blur"]').trigger('click')
    await wrapper.find('[data-testid="code-tab-textarea"]').setValue('changed')
    await wrapper.find('[data-testid="code-tab-textarea"]').trigger('blur')

    expect(wrapper.emitted('save')).toEqual([['changed']])
  })

  it('does not autosave on blur when an interval mode is picked', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="code-tab-autosave-5s"]').trigger('click')
    await wrapper.find('[data-testid="code-tab-textarea"]').setValue('changed')
    await wrapper.find('[data-testid="code-tab-textarea"]').trigger('blur')

    expect(wrapper.emitted('save')).toBeUndefined()
  })

  it('autosaves on the picked interval when dirty', async () => {
    vi.useFakeTimers()
    wrapper = mountTab()

    await wrapper.find('[data-testid="code-tab-autosave-5s"]').trigger('click')
    await wrapper.find('[data-testid="code-tab-textarea"]').setValue('changed')

    vi.advanceTimersByTime(5000)
    expect(wrapper.emitted('save')).toEqual([['changed']])

    vi.advanceTimersByTime(5000)
    expect(wrapper.emitted('save')).toEqual([['changed']])
  })

  it('persists the autosave mode', async () => {
    wrapper = mountTab()
    await wrapper.find('[data-testid="code-tab-autosave-1min"]').trigger('click')
    wrapper.unmount()

    const stored = window.localStorage.getItem('cbs-nova:code-tab:autosave-mode')
    expect(stored).toBe(JSON.stringify('1min'))

    wrapper = mountTab()
    expect(wrapper.find('[data-testid="code-tab-autosave-1min"]').attributes('aria-pressed')).toBe(
      'true',
    )
  })

  it('renders the save status chip for each status', async () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="draft-save-status"]').exists()).toBe(false)

    await wrapper.setProps({ saveStatus: 'dirty' })
    let status = wrapper.find('[data-testid="draft-save-status"]')
    expect(status.text()).toContain('Unsaved changes')
    expect(status.classes()).toEqual(expect.arrayContaining(['bg-amber-50']))

    await wrapper.setProps({ saveStatus: 'saving' })
    status = wrapper.find('[data-testid="draft-save-status"]')
    expect(status.text()).toContain('Saving')
    expect(status.classes()).toEqual(expect.arrayContaining(['bg-blue-50']))

    await wrapper.setProps({ saveStatus: 'saved', lastSavedAt: new Date() })
    status = wrapper.find('[data-testid="draft-save-status"]')
    expect(status.text()).toMatch(/Saved (just now|\ds ago)/)
    expect(status.classes()).toEqual(expect.arrayContaining(['bg-green-50']))

    await wrapper.setProps({ saveStatus: 'error' })
    status = wrapper.find('[data-testid="draft-save-status"]')
    expect(status.text()).toContain('Save failed')
    expect(status.classes()).toEqual(expect.arrayContaining(['bg-red-50']))
  })

  it('retry button in the error state re-emits save', async () => {
    wrapper = mountTab({ saveStatus: 'error' })

    await wrapper.find('[data-testid="draft-save-retry"]').trigger('click')

    expect(wrapper.emitted('save')).toEqual([['initial']])
  })

  it('marks the editor read-only and shows the read-only placeholder', () => {
    wrapper = mountTab({ readOnly: true })

    const textarea = wrapper.find('[data-testid="code-tab-textarea"]')
    expect(textarea.attributes('readonly')).toBeDefined()
    expect(textarea.attributes('placeholder')).toBe('No code available')
  })

  it('syncs the editor when the code prop changes externally', async () => {
    wrapper = mountTab()

    await wrapper.setProps({ code: 'from server' })

    expect(
      (wrapper.find('[data-testid="code-tab-textarea"]').element as HTMLTextAreaElement).value,
    ).toBe('from server')
    expect(wrapper.find('[data-testid="workbench-dirty-indicator"]').exists()).toBe(false)
  })

  it('emits update:code as the user types', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="code-tab-textarea"]').setValue('typed')

    expect(wrapper.emitted('update:code')?.at(-1)).toEqual(['typed'])
  })
})
