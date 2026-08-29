import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import CancelExecutionConfirmationModal from '../executions/CancelExecutionConfirmationModal.vue'

const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0))

// The modal teleports its content into <body>. Stubbing the Teleport render
// keeps it inside the wrapper so we can drive interactions via the test-utils
// API (matching the convention used in HelperSearchPanel.spec.ts).
const mountModal = (props: Record<string, unknown>) =>
  mount(CancelExecutionConfirmationModal, {
    props,
    global: { stubs: { teleport: true } },
    attachTo: document.body,
  })

describe('CancelExecutionConfirmationModal', () => {
  let wrapper: ReturnType<typeof mountModal> | null = null

  beforeEach(() => {
    document.body.innerHTML = ''
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  it('renders nothing when show is false', () => {
    wrapper = mountModal({ show: false })

    expect(wrapper.find('[data-testid="cancel-confirmation-modal"]').exists()).toBe(false)
  })

  it('renders the dialog with confirm/cancel buttons when show is true', () => {
    wrapper = mountModal({ show: true })

    const modal = wrapper.find('[data-testid="cancel-confirmation-modal"]')
    expect(modal.exists()).toBe(true)
    expect(modal.attributes('role')).toBe('dialog')
    expect(modal.attributes('aria-modal')).toBe('true')

    const cancelButton = wrapper.find('[data-testid="cancel-confirmation-modal-cancel"]')
    const confirmButton = wrapper.find('[data-testid="cancel-confirmation-modal-confirm"]')
    expect(cancelButton.exists()).toBe(true)
    expect(confirmButton.exists()).toBe(true)
    expect(cancelButton.text()).toBe('Keep running')
    expect(confirmButton.text()).toBe('Cancel execution')
  })

  it('shows the execution id when provided', () => {
    wrapper = mountModal({ show: true, executionId: 'exec-abc-123' })

    expect(wrapper.text()).toContain('exec-abc-123')
  })

  it('emits confirm when the confirm button is clicked', async () => {
    wrapper = mountModal({ show: true })

    await wrapper.find('[data-testid="cancel-confirmation-modal-confirm"]').trigger('click')

    expect(wrapper.emitted('confirm')).toBeTruthy()
    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })

  it('emits cancel when the cancel button is clicked', async () => {
    wrapper = mountModal({ show: true })

    await wrapper.find('[data-testid="cancel-confirmation-modal-cancel"]').trigger('click')

    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('emits cancel when the backdrop is clicked', async () => {
    wrapper = mountModal({ show: true })

    await wrapper.find('[data-testid="cancel-confirmation-modal"]').trigger('click')

    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('disables both buttons, updates the label and suppresses emits while busy', async () => {
    wrapper = mountModal({ show: true, busy: true })

    const cancelButton = wrapper.find('[data-testid="cancel-confirmation-modal-cancel"]')
    const confirmButton = wrapper.find('[data-testid="cancel-confirmation-modal-confirm"]')
    expect(cancelButton.attributes('disabled')).toBeDefined()
    expect(confirmButton.attributes('disabled')).toBeDefined()
    expect(confirmButton.text()).toBe('Cancelling…')

    await confirmButton.trigger('click')
    await cancelButton.trigger('click')

    expect(wrapper.emitted('confirm')).toBeFalsy()
    expect(wrapper.emitted('cancel')).toBeFalsy()
  })

  it('moves focus into the dialog when opened', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()

    wrapper = mountModal({ show: true })
    await nextTick()
    await flushPromises()

    const cancelButton = wrapper.find('[data-testid="cancel-confirmation-modal-cancel"]').element
    expect(document.activeElement).toBe(cancelButton)
  })

  it('emits cancel when Escape is pressed', async () => {
    wrapper = mountModal({ show: true })
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

    wrapper = mountModal({ show: true })
    await nextTick()
    await flushPromises()
    expect(document.activeElement).not.toBe(trigger)

    await wrapper.setProps({ show: false })
    await nextTick()
    await flushPromises()

    expect(document.activeElement).toBe(trigger)
  })

  it('cycles focus within the dialog with Tab', async () => {
    wrapper = mountModal({ show: true })
    await nextTick()
    await flushPromises()

    const cancelButton = wrapper.find('[data-testid="cancel-confirmation-modal-cancel"]')
      .element as HTMLElement
    const confirmButton = wrapper.find('[data-testid="cancel-confirmation-modal-confirm"]')
      .element as HTMLElement

    confirmButton.focus()
    document.activeElement?.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }),
    )
    await nextTick()

    expect(document.activeElement).toBe(cancelButton)
  })
})
