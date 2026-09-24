import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import { DSL_TEMPLATES } from '../../utils/dslTemplates'
import WorkbenchNewDefinitionModal from '../workbench/WorkbenchNewDefinitionModal.vue'

function mountModal(props: Record<string, unknown>) {
  return mount(WorkbenchNewDefinitionModal, {
    props,
    // DslTemplateGallery is a sibling @cbs/components component the modal
    // embeds; stubbing keeps this spec focused on the modal's own wiring.
    global: {
      stubs: {
        DslTemplateGallery: defineComponent({
          name: 'DslTemplateGallery',
          emits: ['select'],
          setup(_props, { slots }) {
            return () =>
              h(
                'button',
                {
                  'data-testid': 'DslTemplateGallery-stub',
                  onClick: () => slots.default?.(),
                },
                'gallery',
              )
          },
        }),
      },
    },
    attachTo: document.body,
  })
}

describe('WorkbenchNewDefinitionModal', () => {
  it('does not render when closed', () => {
    const wrapper = mountModal({
      open: false,
      newName: '',
      selectedTemplate: null,
      newNameError: null,
    })
    expect(wrapper.find('#workbench-new-title').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renders the title and inputs when open', () => {
    const wrapper = mountModal({
      open: true,
      newName: 'Demo',
      selectedTemplate: DSL_TEMPLATES[0],
      newNameError: null,
    })
    expect(wrapper.find('#workbench-new-title').exists()).toBe(true)
    const input = wrapper.find('[data-testid="workbench-new-name"]')
    expect((input.element as HTMLInputElement).value).toBe('Demo')
    expect(wrapper.find('[data-testid="workbench-new-name-error"]').exists()).toBe(false)
    expect(
      (wrapper.find('[data-testid="workbench-new-create"]').element as HTMLButtonElement).disabled,
    ).toBe(false)
    wrapper.unmount()
  })

  it('shows the inline error and disables Create when the name is invalid', () => {
    const wrapper = mountModal({
      open: true,
      newName: 'bad name!',
      selectedTemplate: DSL_TEMPLATES[0],
      newNameError: 'Name may only contain letters, numbers, dots, dashes and underscores.',
    })
    const error = wrapper.find('[data-testid="workbench-new-name-error"]')
    expect(error.exists()).toBe(true)
    expect(error.text()).toContain('letters, numbers')
    const create = wrapper.find('[data-testid="workbench-new-create"]')
    expect((create.element as HTMLButtonElement).disabled).toBe(true)
    wrapper.unmount()
  })

  it('disables Create when no template has been picked', () => {
    const wrapper = mountModal({
      open: true,
      newName: 'OkName',
      selectedTemplate: null,
      newNameError: null,
    })
    const create = wrapper.find('[data-testid="workbench-new-create"]')
    expect((create.element as HTMLButtonElement).disabled).toBe(true)
    wrapper.unmount()
  })

  it('emits update:new-name when the input changes', async () => {
    const wrapper = mountModal({
      open: true,
      newName: '',
      selectedTemplate: null,
      newNameError: null,
    })
    await wrapper.find('[data-testid="workbench-new-name"]').setValue('Hello')
    const events = wrapper.emitted('update:new-name')
    expect(events).toBeTruthy()
    expect(events?.at(-1)?.[0]).toBe('Hello')
    wrapper.unmount()
  })

  it('emits cancel when backdrop or button clicked', async () => {
    const wrapper = mountModal({
      open: true,
      newName: '',
      selectedTemplate: null,
      newNameError: null,
    })
    await wrapper.find('[data-testid="workbench-new-cancel"]').trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
    wrapper.unmount()
  })

  it('emits create when the Create button is clicked', async () => {
    const wrapper = mountModal({
      open: true,
      newName: 'OkName',
      selectedTemplate: DSL_TEMPLATES[0],
      newNameError: null,
    })
    await wrapper.find('[data-testid="workbench-new-create"]').trigger('click')
    expect(wrapper.emitted('create')).toHaveLength(1)
    wrapper.unmount()
  })
})
