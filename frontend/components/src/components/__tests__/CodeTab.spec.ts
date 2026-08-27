import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CodeTab from '../dsl/CodeTab.vue'

describe('CodeTab', () => {
  it('exposes root data-testid', () => {
    const wrapper = mount(CodeTab, { props: { code: '' } })

    expect(wrapper.find('[data-testid="code-tab"]').exists()).toBe(true)
  })

  it('renders a textarea pre-filled with the code prop', () => {
    const wrapper = mount(CodeTab, { props: { code: 'step Greet {}' } })

    const textarea = wrapper.find('textarea')
    expect(textarea.exists()).toBe(true)
    expect((textarea.element as HTMLTextAreaElement).value).toBe('step Greet {}')
  })

  it('uses the editable placeholder and is not readonly by default', () => {
    const wrapper = mount(CodeTab, { props: { code: '' } })

    const textarea = wrapper.find('textarea')
    expect(textarea.attributes('placeholder')).toBe('Write DSL here...')
    expect(textarea.attributes('readonly')).toBeUndefined()
  })

  it('uses the read-only placeholder and sets readonly when readOnly is true', () => {
    const wrapper = mount(CodeTab, { props: { code: '', readOnly: true } })

    const textarea = wrapper.find('textarea')
    expect(textarea.attributes('placeholder')).toBe('No code available')
    expect(textarea.attributes('readonly')).toBeDefined()
  })

  it('emits update:code as the user types', async () => {
    const wrapper = mount(CodeTab, { props: { code: '' } })

    const textarea = wrapper.find('textarea')
    await textarea.setValue('new code')

    expect(wrapper.emitted('update:code')).toBeTruthy()
    expect(wrapper.emitted('update:code')!.at(-1)).toEqual(['new code'])
  })

  it('syncs the displayed value when the code prop changes externally', async () => {
    const wrapper = mount(CodeTab, { props: { code: 'one' } })

    await wrapper.setProps({ code: 'two' })

    const textarea = wrapper.find('textarea')
    expect((textarea.element as HTMLTextAreaElement).value).toBe('two')
  })
})