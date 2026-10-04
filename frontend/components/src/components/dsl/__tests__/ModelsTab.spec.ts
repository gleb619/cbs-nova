import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import ModelsTab from '../ModelsTab.vue'

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
      data-testid="models-tab-textarea"
      :readonly="readOnly"
      :placeholder="placeholder"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
      @blur="$emit('blur')"
    />`,
  },
}))

const mountTab = (props: Record<string, unknown> = {}) =>
  mount(ModelsTab, { props: { code: 'public class Foo {}', ...props } })

const openAutosaveDropdown = async (wrapper: ReturnType<typeof mountTab>) => {
  await wrapper.find('[data-testid="models-tab-save-dropdown-toggle"]').trigger('click')
}

describe('ModelsTab', () => {
  let wrapper: ReturnType<typeof mountTab>

  beforeEach(() => {
    window.localStorage.clear()
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.useRealTimers()
  })

  it('renders the header label with the construct name', () => {
    wrapper = mountTab({ name: 'CreateOrder' })

    expect(wrapper.find('[data-testid="models-tab-header-label"]').text()).toBe(
      'Models for CreateOrder',
    )
  })

  it('falls back to a generic Models header when no name is given', () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="models-tab-header-label"]').text()).toBe('Models')
  })

  it('hides the toolbar when readOnly', () => {
    wrapper = mountTab({ readOnly: true })

    expect(wrapper.find('[data-testid="models-tab-toolbar"]').exists()).toBe(false)
  })

  it('shows the dirty indicator after editing', async () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="models-tab-dirty-indicator"]').exists()).toBe(false)

    await wrapper.find('[data-testid="models-tab-textarea"]').setValue('changed')

    const indicator = wrapper.find('[data-testid="models-tab-dirty-indicator"]')
    expect(indicator.exists()).toBe(true)
    expect(indicator.text()).toContain('unsaved')
  })

  it('emits save with the current models when Save is clicked', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="models-tab-textarea"]').setValue('public class Bar {}')
    await wrapper.find('[data-testid="models-tab-save"]').trigger('click')

    expect(wrapper.emitted('save')).toEqual([['public class Bar {}']])
    expect(wrapper.find('[data-testid="models-tab-dirty-indicator"]').exists()).toBe(false)
  })

  it('emits update:code as the user types (shared with the Java Code tab API)', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="models-tab-textarea"]').setValue('public class Typed {}')

    expect(wrapper.emitted('update:code')?.at(-1)).toEqual(['public class Typed {}'])
  })

  it('marks the editor read-only and shows the read-only placeholder', () => {
    wrapper = mountTab({ readOnly: true })

    const textarea = wrapper.find('[data-testid="models-tab-textarea"]')
    expect(textarea.attributes('readonly')).toBeDefined()
    expect(textarea.attributes('placeholder')).toBe('No models available')
  })

  it('uses the writable placeholder when editable', () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="models-tab-textarea"]').attributes('placeholder')).toBe(
      'Write model classes here...',
    )
  })

  it('autosaves on blur when the blur mode is picked', async () => {
    wrapper = mountTab()

    await openAutosaveDropdown(wrapper)
    await wrapper.find('[data-testid="models-tab-autosave-blur"]').trigger('click')
    await wrapper.find('[data-testid="models-tab-textarea"]').setValue('public class Blur {}')
    await wrapper.find('[data-testid="models-tab-textarea"]').trigger('blur')

    expect(wrapper.emitted('save')).toEqual([['public class Blur {}']])
  })

  it('autosaves on the picked interval when dirty', async () => {
    vi.useFakeTimers()
    wrapper = mountTab()

    await openAutosaveDropdown(wrapper)
    await wrapper.find('[data-testid="models-tab-autosave-5s"]').trigger('click')
    await wrapper.find('[data-testid="models-tab-textarea"]').setValue('public class Tick {}')

    vi.advanceTimersByTime(5000)
    expect(wrapper.emitted('save')).toEqual([['public class Tick {}']])

    vi.advanceTimersByTime(5000)
    expect(wrapper.emitted('save')).toEqual([['public class Tick {}']])
  })

  it('persists the autosave mode in its own namespace', async () => {
    wrapper = mountTab()
    await openAutosaveDropdown(wrapper)
    await wrapper.find('[data-testid="models-tab-autosave-1min"]').trigger('click')
    wrapper.unmount()

    // Distinct namespace from CodeTab so the two tabs remember independent modes.
    expect(window.localStorage.getItem('cbs-nova:models-tab:autosave-mode')).toBe(
      JSON.stringify('1min'),
    )
    expect(window.localStorage.getItem('cbs-nova:code-tab:autosave-mode')).toBeNull()
  })

  it('renders Refresh and Validate buttons in the toolbar', () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="models-tab-refresh"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="models-tab-validate"]').exists()).toBe(true)
  })

  it('disables Refresh and Validate when no callbacks are provided', () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="models-tab-refresh"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="models-tab-validate"]').attributes('disabled')).toBeDefined()
  })

  it('invokes the refresh callback when Refresh is clicked', async () => {
    const refresh = vi.fn(async () => undefined)
    wrapper = mountTab({ refresh })

    expect(
      wrapper.find('[data-testid="models-tab-refresh"]').attributes('disabled'),
    ).toBeUndefined()

    await wrapper.find('[data-testid="models-tab-refresh"]').trigger('click')

    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('invokes the validate callback when Validate is clicked', async () => {
    const validate = vi.fn(async () => undefined)
    wrapper = mountTab({ validate })

    await wrapper.find('[data-testid="models-tab-validate"]').trigger('click')

    expect(validate).toHaveBeenCalledTimes(1)
  })

  it('disables Refresh and Validate while busy', async () => {
    const refresh = vi.fn(async () => undefined)
    const validate = vi.fn(async () => undefined)
    wrapper = mountTab({ refresh, validate })

    await wrapper.setProps({ busy: true })

    expect(wrapper.find('[data-testid="models-tab-refresh"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="models-tab-validate"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-testid="models-tab-refresh"]').trigger('click')
    await wrapper.find('[data-testid="models-tab-validate"]').trigger('click')

    expect(refresh).not.toHaveBeenCalled()
    expect(validate).not.toHaveBeenCalled()
  })

  it('hides Refresh and Validate when readOnly', () => {
    wrapper = mountTab({
      readOnly: true,
      refresh: vi.fn(),
      validate: vi.fn(),
    })

    expect(wrapper.find('[data-testid="models-tab-refresh"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="models-tab-validate"]').exists()).toBe(false)
  })

  describe('hotkeys', () => {
    function dispatchWindowKey(opts: KeyboardEventInit & { key: string }) {
      window.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, ...opts }))
    }

    it('triggers save on Ctrl+S when dirty', async () => {
      wrapper = mountTab()

      await wrapper.find('[data-testid="models-tab-textarea"]').setValue('public class Shortcut {}')
      dispatchWindowKey({ key: 's', ctrlKey: true })

      expect(wrapper.emitted('save')).toEqual([['public class Shortcut {}']])
    })

    it('triggers refresh on Ctrl+Shift+R', async () => {
      const refresh = vi.fn(async () => undefined)
      wrapper = mountTab({ refresh })

      dispatchWindowKey({ key: 'r', ctrlKey: true, shiftKey: true })

      expect(refresh).toHaveBeenCalledTimes(1)
    })

    it('does not trigger refresh on plain Ctrl+R', async () => {
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

    it('suppresses hotkeys while busy', async () => {
      const refresh = vi.fn(async () => undefined)
      const validate = vi.fn(async () => undefined)
      wrapper = mountTab({ refresh, validate })
      await wrapper.find('[data-testid="models-tab-textarea"]').setValue('changed')

      await wrapper.setProps({ busy: true })

      dispatchWindowKey({ key: 's', ctrlKey: true })
      dispatchWindowKey({ key: 'r', ctrlKey: true, shiftKey: true })
      dispatchWindowKey({ key: 'Enter', ctrlKey: true })

      expect(wrapper.emitted('save')).toBeUndefined()
      expect(refresh).not.toHaveBeenCalled()
      expect(validate).not.toHaveBeenCalled()
    })

    it('ignores the save shortcut when focus is in an editable element', async () => {
      wrapper = mountTab()
      const textarea = wrapper.find('[data-testid="models-tab-textarea"]')
      await textarea.setValue('public class Inner {}')

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

  it('renders the save status chip for each status', async () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="models-tab-save-status"]').exists()).toBe(false)

    await wrapper.setProps({ saveStatus: 'dirty' })
    let status = wrapper.find('[data-testid="models-tab-save-status"]')
    expect(status.text()).toContain('Unsaved changes')
    expect(status.classes()).toEqual(expect.arrayContaining(['bg-amber-50']))

    await wrapper.setProps({ saveStatus: 'saving' })
    status = wrapper.find('[data-testid="models-tab-save-status"]')
    expect(status.text()).toContain('Saving')

    await wrapper.setProps({ saveStatus: 'saved', lastSavedAt: new Date() })
    status = wrapper.find('[data-testid="models-tab-save-status"]')
    expect(status.text()).toMatch(/Saved (just now|\ds ago)/)
    expect(status.classes()).toEqual(expect.arrayContaining(['bg-green-50']))

    await wrapper.setProps({ saveStatus: 'error' })
    status = wrapper.find('[data-testid="models-tab-save-status"]')
    expect(status.text()).toContain('Save failed')
    expect(status.classes()).toEqual(expect.arrayContaining(['bg-red-50']))
  })
})
