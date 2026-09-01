import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import DslTemplateGallery from '../DslTemplateGallery.vue'
import { DSL_TEMPLATES, type DslTemplate } from '../../utils/dslTemplates'

describe('DslTemplateGallery', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('exposes the root test id and renders all default templates', () => {
    const wrapper = mount(DslTemplateGallery)

    expect(wrapper.find('[data-testid="dsl-template-gallery"]').exists()).toBe(true)

    for (const template of DSL_TEMPLATES) {
      const item = wrapper.find(`[data-testid="dsl-template-${template.id}"]`)
      expect(item.exists()).toBe(true)
      expect(item.text()).toContain(template.label)
      expect(item.text()).toContain(template.description)
    }
  })

  it('emits select with the chosen template when a card is clicked', async () => {
    const wrapper = mount(DslTemplateGallery)

    for (const template of DSL_TEMPLATES) {
      const item = wrapper.find(`[data-testid="dsl-template-${template.id}"]`)
      await item.trigger('click')

      expect(wrapper.emitted('select')).toBeTruthy()
      const events = wrapper.emitted('select') as [DslTemplate][]
      const last = events.at(-1)
      expect(last?.[0]).toMatchObject(template)
    }
  })

  it('can render a custom templates prop', () => {
    const custom: DslTemplate[] = [
      {
        id: 'custom-one',
        label: 'Custom One',
        description: 'First custom template.',
        body: '{}',
      },
      {
        id: 'custom-two',
        label: 'Custom Two',
        description: 'Second custom template.',
        body: '{}',
      },
    ]

    const wrapper = mount(DslTemplateGallery, { props: { templates: custom } })

    expect(wrapper.findAll('[data-testid^="dsl-template-"][role="option"]')).toHaveLength(2)
    expect(wrapper.find('[data-testid="dsl-template-custom-one"]').text()).toContain('Custom One')
    expect(wrapper.find('[data-testid="dsl-template-custom-two"]').text()).toContain('Custom Two')
  })

  it('is keyboard accessible as a listbox', () => {
    const wrapper = mount(DslTemplateGallery)

    const root = wrapper.find('[data-testid="dsl-template-gallery"]')
    expect(root.attributes('role')).toBe('listbox')
    expect(root.attributes('aria-label')).toBe('Starter templates')

    const firstItem = wrapper.find('[data-testid="dsl-template-plain-process"]')
    expect(firstItem.attributes('role')).toBe('option')
  })
})
