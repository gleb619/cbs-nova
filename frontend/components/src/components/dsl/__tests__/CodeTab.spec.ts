import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
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

  it('treats the code prop as initial value (external refresh via remount)', async () => {
    wrapper = mountTab()

    await wrapper.setProps({ code: 'from server' })

    expect(
      (wrapper.find('[data-testid="code-tab-textarea"]').element as HTMLTextAreaElement).value,
    ).toBe('initial')
    expect(wrapper.find('[data-testid="workbench-dirty-indicator"]').exists()).toBe(false)
  })

  it('emits update:code as the user types', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="code-tab-textarea"]').setValue('typed')

    expect(wrapper.emitted('update:code')?.at(-1)).toEqual(['typed'])
  })

  it('renders Refresh and Validate buttons in the toolbar', () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="code-tab-refresh"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="code-tab-validate"]').exists()).toBe(true)
  })

  it('disables Refresh and Validate when no callbacks are provided', () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="code-tab-refresh"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="code-tab-validate"]').attributes('disabled')).toBeDefined()
  })

  it('invokes the refresh callback when Refresh is clicked', async () => {
    const refresh = vi.fn(async () => undefined)
    wrapper = mountTab({ refresh })

    expect(wrapper.find('[data-testid="code-tab-refresh"]').attributes('disabled')).toBeUndefined()

    await wrapper.find('[data-testid="code-tab-refresh"]').trigger('click')

    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('invokes the validate callback when Validate is clicked', async () => {
    const validate = vi.fn(async () => undefined)
    wrapper = mountTab({ validate })

    await wrapper.find('[data-testid="code-tab-validate"]').trigger('click')

    expect(validate).toHaveBeenCalledTimes(1)
  })

  it('disables Refresh and Validate while busy', async () => {
    const refresh = vi.fn(async () => undefined)
    const validate = vi.fn(async () => undefined)
    wrapper = mountTab({ refresh, validate })

    await wrapper.setProps({ busy: true })

    expect(wrapper.find('[data-testid="code-tab-refresh"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="code-tab-validate"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-testid="code-tab-refresh"]').trigger('click')
    await wrapper.find('[data-testid="code-tab-validate"]').trigger('click')

    expect(refresh).not.toHaveBeenCalled()
    expect(validate).not.toHaveBeenCalled()
  })

  it('hides Refresh and Validate when readOnly', () => {
    wrapper = mountTab({
      readOnly: true,
      refresh: vi.fn(),
      validate: vi.fn(),
    })

    expect(wrapper.find('[data-testid="code-tab-refresh"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="code-tab-validate"]').exists()).toBe(false)
  })

  describe('hotkeys', () => {
    function dispatchWindowKey(opts: KeyboardEventInit & { key: string }) {
      window.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, ...opts }))
    }

    it('triggers save on Ctrl+S when dirty', async () => {
      wrapper = mountTab()

      await wrapper.find('[data-testid="code-tab-textarea"]').setValue('changed')
      dispatchWindowKey({ key: 's', ctrlKey: true })

      expect(wrapper.emitted('save')).toEqual([['changed']])
    })

    it('ignores Ctrl+S when there are no unsaved changes', async () => {
      wrapper = mountTab()

      dispatchWindowKey({ key: 's', ctrlKey: true })

      expect(wrapper.emitted('save')).toBeUndefined()
    })

    it('does not trigger hotkeys when readOnly', async () => {
      const refresh = vi.fn()
      const validate = vi.fn()
      wrapper = mountTab({ readOnly: true, refresh, validate })

      dispatchWindowKey({ key: 's', ctrlKey: true })
      dispatchWindowKey({ key: 'r', ctrlKey: true, shiftKey: true })
      dispatchWindowKey({ key: 'Enter', ctrlKey: true })

      expect(refresh).not.toHaveBeenCalled()
      expect(validate).not.toHaveBeenCalled()
      expect(wrapper.emitted('save')).toBeUndefined()
    })

    it('triggers refresh on Ctrl+Shift+R', async () => {
      const refresh = vi.fn(async () => undefined)
      wrapper = mountTab({ refresh })

      dispatchWindowKey({ key: 'r', ctrlKey: true, shiftKey: true })

      expect(refresh).toHaveBeenCalledTimes(1)
    })

    it('does not trigger refresh on plain Ctrl+R (reserved for browser reload)', async () => {
      const refresh = vi.fn(async () => undefined)
      wrapper = mountTab({ refresh })

      dispatchWindowKey({ key: 'r', ctrlKey: true })

      expect(refresh).not.toHaveBeenCalled()
    })

    it('triggers validate on Ctrl+Enter', async () => {
      const validate = vi.fn(async () => undefined)
      wrapper = mountTab({ validate })

      dispatchWindowKey({ key: 'Enter', ctrlKey: true })

      expect(validate).toHaveBeenCalledTimes(1)
    })

    it('suppresses hotkeys while busy', async () => {
      const refresh = vi.fn(async () => undefined)
      const validate = vi.fn(async () => undefined)
      wrapper = mountTab({ refresh, validate })
      await wrapper.find('[data-testid="code-tab-textarea"]').setValue('changed')

      await wrapper.setProps({ busy: true })

      dispatchWindowKey({ key: 's', ctrlKey: true })
      dispatchWindowKey({ key: 'r', ctrlKey: true, shiftKey: true })
      dispatchWindowKey({ key: 'Enter', ctrlKey: true })

      expect(wrapper.emitted('save')).toBeUndefined()
      expect(refresh).not.toHaveBeenCalled()
      expect(validate).not.toHaveBeenCalled()
    })

    it('uses Meta on Mac for the save shortcut label', async () => {
      Object.defineProperty(navigator, 'platform', {
        value: 'MacIntel',
        configurable: true,
      })

      wrapper = mountTab({ refresh: vi.fn(), validate: vi.fn() })
      await wrapper.find('[data-testid="code-tab-textarea"]').setValue('changed')

      dispatchWindowKey({ key: 's', metaKey: true })

      expect(wrapper.emitted('save')).toEqual([['changed']])
    })

    it('ignores the save shortcut when focus is in an editable element', async () => {
      wrapper = mountTab()
      const textarea = wrapper.find('[data-testid="code-tab-textarea"]')
      await textarea.setValue('changed')

      await textarea.trigger('keydown', { key: 's', ctrlKey: true })

      expect(wrapper.emitted('save')).toBeUndefined()
    })

    it('renders tooltip hints bound to the active modifier', async () => {
      Object.defineProperty(navigator, 'platform', { value: 'Win32', configurable: true })
      wrapper = mountTab({ refresh: vi.fn(), validate: vi.fn() })

      // Trigger Alt to reveal the hotkey overlay.
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Alt' }))
      await nextTick()

      const hints = wrapper.findAll('[data-testid="hotkey-tooltip"]').map((node) => node.text())
      expect(hints).toEqual(expect.arrayContaining(['Ctrl+S', 'Ctrl+Shift+R', 'Ctrl+Enter']))
    })
  })
})
