import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import SaveAction, { type AutosaveOption } from '../SaveAction.vue'

const AUTOSAVE: AutosaveOption[] = [
  { value: 'off', label: 'Off' },
  { value: '5s', label: '5s' },
  { value: 'blur', label: 'On blur' },
]

const mountAction = (
  props: Record<string, unknown> = {},
  options: Record<string, unknown> = {},
) =>
  mount(SaveAction, {
    props: { testidPrefix: 'sa', isDirty: false, ...props },
    attachTo: document.body,
    ...options,
  })

describe('SaveAction', () => {
  let wrapper: ReturnType<typeof mountAction>

  afterEach(() => {
    wrapper?.unmount()
  })

  it('renders the Save button disabled when not dirty', () => {
    wrapper = mountAction()

    const btn = wrapper.find('[data-testid="sa-save"]')
    expect(btn.exists()).toBe(true)
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('renders the Save button enabled when dirty', async () => {
    wrapper = mountAction({ isDirty: true })

    const btn = wrapper.find('[data-testid="sa-save"]')
    expect(btn.attributes('disabled')).toBeUndefined()
  })

  it('hides the dropdown chevron when no autosave options are provided', () => {
    wrapper = mountAction({ isDirty: true })

    expect(wrapper.find('[data-testid="sa-save-dropdown-toggle"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="sa-save-dropdown"]').exists()).toBe(false)
  })

  it('emits save when the Save button is clicked and dirty', async () => {
    wrapper = mountAction({ isDirty: true })

    await wrapper.find('[data-testid="sa-save"]').trigger('click')

    expect(wrapper.emitted('save')).toHaveLength(1)
  })

  it('does not emit save when not dirty', async () => {
    wrapper = mountAction({ isDirty: false })

    await wrapper.find('[data-testid="sa-save"]').trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
  })

  it('does not emit save while busy', async () => {
    wrapper = mountAction({ isDirty: true, busy: true })

    await wrapper.find('[data-testid="sa-save"]').trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
  })

  it('renders the dirty indicator only when dirty', () => {
    wrapper = mountAction({ isDirty: false })
    expect(wrapper.find('[data-testid="sa-dirty-indicator"]').exists()).toBe(false)

    wrapper.unmount()
    wrapper = mountAction({ isDirty: true })
    const dot = wrapper.find('[data-testid="sa-dirty-indicator"]')
    expect(dot.exists()).toBe(true)
    expect(dot.text()).toContain('unsaved')
  })

  it('renders the save status chip for each status', async () => {
    wrapper = mountAction()

    expect(wrapper.find('[data-testid="sa-save-status"]').exists()).toBe(false)

    await wrapper.setProps({ saveStatus: 'dirty' })
    expect(wrapper.find('[data-testid="sa-save-status"]').text()).toContain('Unsaved changes')

    await wrapper.setProps({ saveStatus: 'saving' })
    expect(wrapper.find('[data-testid="sa-save-status"]').text()).toContain('Saving')

    await wrapper.setProps({ saveStatus: 'saved', lastSavedAt: new Date() })
    expect(wrapper.find('[data-testid="sa-save-status"]').text()).toMatch(/Saved/)

    await wrapper.setProps({ saveStatus: 'error' })
    expect(wrapper.find('[data-testid="sa-save-status"]').text()).toContain('Save failed')
  })

  it('retry button in the error state re-emits save', async () => {
    wrapper = mountAction({ isDirty: true, saveStatus: 'error' })

    await wrapper.find('[data-testid="sa-save-retry"]').trigger('click')

    expect(wrapper.emitted('save')).toHaveLength(1)
  })

  it('opens the dropdown when the chevron is clicked', async () => {
    wrapper = mountAction({ isDirty: true, autosaveOptions: AUTOSAVE, autosaveMode: 'off' })

    expect(wrapper.find('[data-testid="sa-save-dropdown"]').exists()).toBe(false)

    const toggle = wrapper.find('[data-testid="sa-save-dropdown-toggle"]')
    await toggle.trigger('click')

    const dropdown = wrapper.find('[data-testid="sa-save-dropdown"]')
    expect(dropdown.exists()).toBe(true)
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(dropdown.findAll('[data-testid^="sa-autosave-"]')).toHaveLength(AUTOSAVE.length)
  })

  it('emits update:autosaveMode and closes the dropdown when an option is picked', async () => {
    wrapper = mountAction({ isDirty: true, autosaveOptions: AUTOSAVE, autosaveMode: 'off' })

    await wrapper.find('[data-testid="sa-save-dropdown-toggle"]').trigger('click')
    await wrapper.find('[data-testid="sa-autosave-5s"]').trigger('click')

    expect(wrapper.emitted('update:autosaveMode')).toEqual([['5s']])
    expect(wrapper.find('[data-testid="sa-save-dropdown"]').exists()).toBe(false)
  })

  it('marks the currently selected autosave mode in the dropdown', async () => {
    wrapper = mountAction({ isDirty: true, autosaveOptions: AUTOSAVE, autosaveMode: 'blur' })

    await wrapper.find('[data-testid="sa-save-dropdown-toggle"]').trigger('click')

    const selected = wrapper.find('[data-testid="sa-autosave-blur"]')
    expect(selected.classes()).toEqual(expect.arrayContaining(['font-semibold']))
    expect(selected.text()).toContain('✓')
  })

  it('closes the dropdown when clicking outside the root', async () => {
    wrapper = mountAction({ isDirty: true, autosaveOptions: AUTOSAVE, autosaveMode: 'off' })

    await wrapper.find('[data-testid="sa-save-dropdown-toggle"]').trigger('click')
    expect(wrapper.find('[data-testid="sa-save-dropdown"]').exists()).toBe(true)

    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await nextTick()

    expect(wrapper.find('[data-testid="sa-save-dropdown"]').exists()).toBe(false)
  })

  it('closes the dropdown on Escape', async () => {
    wrapper = mountAction({ isDirty: true, autosaveOptions: AUTOSAVE, autosaveMode: 'off' })

    await wrapper.find('[data-testid="sa-save-dropdown-toggle"]').trigger('click')
    expect(wrapper.find('[data-testid="sa-save-dropdown"]').exists()).toBe(true)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()

    expect(wrapper.find('[data-testid="sa-save-dropdown"]').exists()).toBe(false)
  })

  it('toggles the dropdown when the chevron is clicked twice', async () => {
    wrapper = mountAction({ isDirty: true, autosaveOptions: AUTOSAVE, autosaveMode: 'off' })

    const toggle = wrapper.find('[data-testid="sa-save-dropdown-toggle"]')
    await toggle.trigger('click')
    expect(wrapper.find('[data-testid="sa-save-dropdown"]').exists()).toBe(true)

    await toggle.trigger('click')
    expect(wrapper.find('[data-testid="sa-save-dropdown"]').exists()).toBe(false)
  })
})
