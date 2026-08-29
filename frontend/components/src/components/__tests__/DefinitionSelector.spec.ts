import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { DefinitionMeta } from '../../types/runner'
import DefinitionSelector from '../runner/DefinitionSelector.vue'

const definitions: DefinitionMeta[] = [
  { name: 'DefA', type: 'process' },
  { name: 'DefB', type: 'function' },
]

describe('DefinitionSelector', () => {
  it('renders the root data-testid, placeholder option and definition options', () => {
    const wrapper = mount(DefinitionSelector, {
      props: { definitions, modelValue: null },
    })

    expect(wrapper.find('[data-testid="definition-selector"]').exists()).toBe(true)
    const options = wrapper.find('select').findAll('option')
    expect(options.length).toBe(3)
    expect(options[0].text()).toContain('Select a definition')
    expect(options[1].text()).toContain('DefA')
    expect(options[1].text()).toContain('process')
    expect(options[2].text()).toContain('DefB')
    expect(options[2].text()).toContain('function')
  })

  it('emits update:modelValue with the selected definition name', async () => {
    const wrapper = mount(DefinitionSelector, {
      props: { definitions, modelValue: null },
    })

    await wrapper.find('select').setValue('DefA')

    expect(wrapper.emitted('update:modelValue')).toEqual([['DefA']])
  })

  it('reflects the current modelValue in the select value', () => {
    const wrapper = mount(DefinitionSelector, {
      props: { definitions, modelValue: 'DefB' },
    })

    expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('DefB')
  })
})
