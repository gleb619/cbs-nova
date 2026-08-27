import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CodeTab from '../dsl/CodeTab.vue'

describe('CodeTab', () => {
  it('exposes root data-testid', () => {
    const wrapper = mount(CodeTab, { props: { code: '' } })

    expect(wrapper.find('[data-testid="code-tab"]').exists()).toBe(true)
  })

  it('renders the syntax-highlighted editor surface pre-filled with the code prop', () => {
    const wrapper = mount(CodeTab, { props: { code: 'step Greet {}' } })

    // The new editor surface pairs a Prism-highlighted <pre> with an
    // overlaid <textarea> that captures user input.
    const editor = wrapper.find('[data-testid="code-tab-editor"]')
    expect(editor.exists()).toBe(true)

    const highlight = wrapper.find('[data-testid="code-tab-highlight"]')
    expect(highlight.exists()).toBe(true)
    // Prism tokenizes identifiers like Greet, so assert on the display's
    // text content rather than its raw HTML.
    expect(highlight.text()).toContain('step Greet {}')

    const textarea = wrapper.find('textarea[data-testid="code-tab-textarea"]')
    expect(textarea.exists()).toBe(true)
    expect((textarea.element as HTMLTextAreaElement).value).toBe('step Greet {}')
  })

  it('uses the editable placeholder and is not readonly by default', () => {
    const wrapper = mount(CodeTab, { props: { code: '' } })

    const textarea = wrapper.find('textarea[data-testid="code-tab-textarea"]')
    expect(textarea.attributes('placeholder')).toBe('Write DSL here...')
    expect(textarea.attributes('readonly')).toBeUndefined()
  })

  it('uses the read-only placeholder and sets readonly when readOnly is true', () => {
    const wrapper = mount(CodeTab, { props: { code: '', readOnly: true } })

    // Read-only mode still keeps a (transparent) textarea in the DOM so the
    // editor surface remains queryable, but it now layers a highlighted
    // <pre data-testid="code-tab-display"> on top.
    const textarea = wrapper.find('textarea[data-testid="code-tab-textarea"]')
    expect(textarea.exists()).toBe(true)
    expect(textarea.attributes('placeholder')).toBe('No code available')
    expect(textarea.attributes('readonly')).toBeDefined()

    const display = wrapper.find('[data-testid="code-tab-display"]')
    expect(display.exists()).toBe(true)
  })

  it('emits update:code as the user types', async () => {
    const wrapper = mount(CodeTab, { props: { code: '' } })

    const textarea = wrapper.find('textarea[data-testid="code-tab-textarea"]')
    await textarea.setValue('new code')

    expect(wrapper.emitted('update:code')).toBeTruthy()
    expect(wrapper.emitted('update:code')?.at(-1)).toEqual(['new code'])
  })

  it('syncs the displayed value when the code prop changes externally', async () => {
    const wrapper = mount(CodeTab, { props: { code: 'one' } })

    await wrapper.setProps({ code: 'two' })

    const textarea = wrapper.find('textarea[data-testid="code-tab-textarea"]')
    expect((textarea.element as HTMLTextAreaElement).value).toBe('two')
    expect(wrapper.find('[data-testid="code-tab-highlight"]').text()).toContain('two')
  })
})
