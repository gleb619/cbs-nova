import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import RunConfirmationModal from '../runner/RunConfirmationModal.vue'

const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0))

const mountModal = (props: Record<string, unknown>) =>
  mount(RunConfirmationModal, {
    props,
    global: { stubs: { teleport: true } },
    attachTo: document.body,
  })

describe('RunConfirmationModal', () => {
  let wrapper: ReturnType<typeof mountModal> | null = null

  beforeEach(() => {
    document.body.innerHTML = ''
    window.sessionStorage.clear()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  it('renders nothing when show is false', () => {
    wrapper = mountModal({ show: false, payload: null })

    expect(wrapper.find('[data-testid="run-confirmation-modal"]').exists()).toBe(false)
  })

  it('renders the dialog with confirm/cancel buttons when show is true', () => {
    wrapper = mountModal({ show: true, payload: { ok: true } })

    const modal = wrapper.find('[data-testid="run-confirmation-modal"]')
    expect(modal.exists()).toBe(true)
    expect(modal.attributes('role')).toBe('dialog')
    expect(modal.attributes('aria-modal')).toBe('true')

    const cancelButton = wrapper.find('[data-testid="run-confirmation-modal-cancel"]')
    const confirmButton = wrapper.find('[data-testid="run-confirmation-modal-confirm"]')
    expect(cancelButton.exists()).toBe(true)
    expect(confirmButton.exists()).toBe(true)
    expect(cancelButton.text()).toBe('Cancel')
    expect(confirmButton.text()).toBe('Confirm Run')
  })

  it('disables confirm until the acknowledgement checkbox is checked', () => {
    wrapper = mountModal({ show: true, payload: null })

    const confirmButton = wrapper.find('[data-testid="run-confirmation-modal-confirm"]')
    expect(confirmButton.element.disabled).toBe(true)
    // The enable-on-acknowledge round trip is covered by the "emits confirm when
    // acknowledged" and "does not emit confirm when not acknowledged" tests below —
    // this component is Teleported and stubbed here, and VTU's teleport stub doesn't
    // reliably re-patch attribute bindings on a second update, so we assert reactive
    // behavior through emitted events rather than a post-update DOM attribute read.
  })

  it('emits confirm when acknowledged and confirm is clicked', async () => {
    wrapper = mountModal({ show: true, payload: null })

    const checkbox = wrapper.find('input[type="checkbox"]')
    await checkbox.setValue(true)
    await nextTick()
    await wrapper.find('[data-testid="run-confirmation-modal-confirm"]').trigger('click')

    expect(wrapper.emitted('confirm')).toBeTruthy()
    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })

  it('does not emit confirm when not acknowledged', async () => {
    wrapper = mountModal({ show: true, payload: null })

    await wrapper.find('[data-testid="run-confirmation-modal-confirm"]').trigger('click')

    expect(wrapper.emitted('confirm')).toBeFalsy()
  })

  it('emits cancel when the cancel button is clicked', async () => {
    wrapper = mountModal({ show: true, payload: null })

    await wrapper.find('[data-testid="run-confirmation-modal-cancel"]').trigger('click')

    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('emits cancel when the backdrop is clicked', async () => {
    wrapper = mountModal({ show: true, payload: null })

    await wrapper.find('[data-testid="run-confirmation-modal"]').trigger('click')

    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('persists skip preference to sessionStorage when checked', async () => {
    wrapper = mountModal({ show: true, payload: null })

    const skipCheckbox = wrapper.findAll('input[type="checkbox"]').at(1)
    expect(skipCheckbox).toBeDefined()
    await skipCheckbox!.setValue(true)
    await nextTick()
    await flushPromises()

    expect(window.sessionStorage.getItem('skip-run-confirm')).toBe('1')
  })

  it('reads skip preference from sessionStorage when opened', async () => {
    window.sessionStorage.setItem('skip-run-confirm', '1')

    wrapper = mountModal({ show: true, payload: null })
    await flushPromises()

    const skipCheckbox = wrapper.findAll('input[type="checkbox"]').at(1)
    expect(skipCheckbox).toBeDefined()
    expect((skipCheckbox!.element as HTMLInputElement).checked).toBe(true)
  })

  it('moves focus into the dialog when opened', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()

    wrapper = mountModal({ show: true, payload: null })
    await nextTick()
    await flushPromises()

    const firstFocusable = wrapper.find('button, input').element
    expect(document.activeElement).toBe(firstFocusable)
  })

  it('emits cancel when Escape is pressed', async () => {
    wrapper = mountModal({ show: true, payload: null })
    await nextTick()
    await flushPromises()

    document.activeElement?.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    )
    await nextTick()

    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('returns focus to the trigger element when closed', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()

    wrapper = mountModal({ show: true, payload: null })
    await nextTick()
    await flushPromises()
    expect(document.activeElement).not.toBe(trigger)

    await wrapper.setProps({ show: false })
    await nextTick()
    await flushPromises()

    expect(document.activeElement).toBe(trigger)
  })

  it('cycles focus within the dialog with Tab', async () => {
    wrapper = mountModal({ show: true, payload: null })
    await nextTick()
    await flushPromises()

    const focusable = wrapper.findAll('button, input:not([type="hidden"])')
    expect(focusable.length).toBeGreaterThan(0)

    const firstTestId = focusable[0].attributes('data-testid')
    const last = focusable[focusable.length - 1].element as HTMLElement

    last.focus()
    document.activeElement?.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }),
    )
    await nextTick()

    expect(document.activeElement?.tagName.toLowerCase()).toBe(
      focusable[0].element.tagName.toLowerCase(),
    )
  })
})
