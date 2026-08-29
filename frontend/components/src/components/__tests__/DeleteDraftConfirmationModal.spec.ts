import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import DeleteDraftConfirmationModal from '../dsl/DeleteDraftConfirmationModal.vue'

const mountModal = (props: Record<string, unknown>) =>
  mount(DeleteDraftConfirmationModal, {
    props,
    global: { stubs: { teleport: true } },
  })

describe('DeleteDraftConfirmationModal', () => {
  let wrapper: ReturnType<typeof mountModal> | null = null

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  it('renders nothing when show is false', () => {
    wrapper = mountModal({ show: false })

    expect(wrapper.find('[data-testid="delete-draft-confirmation-modal"]').exists()).toBe(false)
  })

  it('renders the dialog with confirm/cancel buttons when show is true', () => {
    wrapper = mountModal({ show: true, draftName: 'DraftOne' })

    const modal = wrapper.find('[data-testid="delete-draft-confirmation-modal"]')
    expect(modal.exists()).toBe(true)
    expect(modal.attributes('role')).toBe('dialog')
    expect(modal.attributes('aria-modal')).toBe('true')
    expect(wrapper.text()).toContain('DraftOne')

    const cancelButton = wrapper.find('[data-testid="delete-draft-confirmation-modal-cancel"]')
    const confirmButton = wrapper.find('[data-testid="delete-draft-confirmation-modal-confirm"]')
    expect(cancelButton.exists()).toBe(true)
    expect(confirmButton.exists()).toBe(true)
    expect(cancelButton.text()).toBe('Keep draft')
    expect(confirmButton.text()).toBe('Delete draft')
  })

  it('emits confirm when the confirm button is clicked', async () => {
    wrapper = mountModal({ show: true })

    await wrapper.find('[data-testid="delete-draft-confirmation-modal-confirm"]').trigger('click')

    expect(wrapper.emitted('confirm')).toBeTruthy()
    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })

  it('emits cancel when the cancel button is clicked', async () => {
    wrapper = mountModal({ show: true })

    await wrapper.find('[data-testid="delete-draft-confirmation-modal-cancel"]').trigger('click')

    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('emits cancel when the backdrop is clicked', async () => {
    wrapper = mountModal({ show: true })

    await wrapper.find('[data-testid="delete-draft-confirmation-modal"]').trigger('click')

    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('disables both buttons and suppresses emits while busy', async () => {
    wrapper = mountModal({ show: true, busy: true })

    const cancelButton = wrapper.find('[data-testid="delete-draft-confirmation-modal-cancel"]')
    const confirmButton = wrapper.find('[data-testid="delete-draft-confirmation-modal-confirm"]')
    expect(cancelButton.attributes('disabled')).toBeDefined()
    expect(confirmButton.attributes('disabled')).toBeDefined()
    expect(confirmButton.text()).toBe('Deleting…')

    await confirmButton.trigger('click')
    await cancelButton.trigger('click')

    expect(wrapper.emitted('confirm')).toBeFalsy()
    expect(wrapper.emitted('cancel')).toBeFalsy()
  })
})
