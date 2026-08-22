import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { DslConstruct } from '../../types/dsl'
import BodyEditor from '../dsl/BodyEditor.vue'
import CodeTab from '../dsl/CodeTab.vue'
import StructureTab from '../dsl/StructureTab.vue'

const construct: DslConstruct = {
  name: 'CreateOrder',
  type: 'Process',
  status: 'Valid',
}

function mountBodyEditor(props: Record<string, unknown>) {
  // BodyEditor references StructureTab / CodeTab in its template without importing
  // them (they are Nuxt auto-imported in the host app). Register real children here
  // so they resolve under vitest, mirroring how OutputPanel registers its children.
  return mount(BodyEditor, {
    props,
    global: { components: { StructureTab, CodeTab } },
  })
}

describe('BodyEditor', () => {
  it('renders the Structure and Code tab buttons with Structure active by default', () => {
    const wrapper = mountBodyEditor({ construct })

    const buttons = wrapper.findAll('button').map((b) => b.text())
    expect(buttons).toContain('Structure')
    expect(buttons).toContain('Code')

    const structureButton = wrapper.findAll('button').find((b) => b.text() === 'Structure')!
    const codeButton = wrapper.findAll('button').find((b) => b.text() === 'Code')!
    expect(structureButton.classes()).toContain('border-blue-500')
    expect(codeButton.classes()).not.toContain('border-blue-500')
  })

  it('switches the active tab to Code when the Code tab is clicked', async () => {
    const wrapper = mountBodyEditor({ construct })

    const codeButton = wrapper.findAll('button').find((b) => b.text() === 'Code')!
    await codeButton.trigger('click')

    const structureButton = wrapper.findAll('button').find((b) => b.text() === 'Structure')!
    expect(structureButton.classes()).not.toContain('border-blue-500')
    expect(codeButton.classes()).toContain('border-blue-500')
    expect(wrapper.find('textarea').exists()).toBe(true)
  })

  it('marks the code editor read-only when no construct is selected', async () => {
    const wrapper = mountBodyEditor({ construct: null, code: 'foo' })

    const codeButton = wrapper.findAll('button').find((b) => b.text() === 'Code')!
    await codeButton.trigger('click')

    const textarea = wrapper.find('textarea')
    expect(textarea.attributes('readonly')).toBeDefined()
    expect(textarea.attributes('placeholder')).toBe('No code available')
  })

  it('leaves the code editor writable when a construct is selected', async () => {
    const wrapper = mountBodyEditor({ construct, code: 'foo' })

    const codeButton = wrapper.findAll('button').find((b) => b.text() === 'Code')!
    await codeButton.trigger('click')

    const textarea = wrapper.find('textarea')
    expect(textarea.attributes('readonly')).toBeUndefined()
    expect(textarea.attributes('placeholder')).toBe('Write DSL here...')
  })

  it('emits update:code when controlled code is edited', async () => {
    const wrapper = mountBodyEditor({ construct, code: 'initial' })

    const codeButton = wrapper.findAll('button').find((b) => b.text() === 'Code')!
    await codeButton.trigger('click')

    await wrapper.find('textarea').setValue('edited body')

    expect(wrapper.emitted('update:code')).toBeTruthy()
    expect(wrapper.emitted('update:code')!.at(-1)).toEqual(['edited body'])
  })
})