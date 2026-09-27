import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DocTab from '../DocTab.vue'

vi.mock('../MonacoEditor.vue', () => ({
  default: {
    name: 'MonacoEditorStub',
    props: {
      modelValue: { type: String, default: '' },
      language: { type: String, default: 'java' },
      readOnly: { type: Boolean, default: false },
      placeholder: { type: String, default: '' },
    },
    emits: ['update:modelValue'],
    template: `<textarea
      data-testid="doc-tab-textarea"
      :readonly="readOnly"
      :placeholder="placeholder"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
    />`,
  },
}))

const SAMPLE_DOC =
  '---\nname: BatchProcessing\ndescription: Sums values\n---\n\n# BatchProcessing\n\nSome text\n'

const mountTab = (props: Record<string, unknown> = {}) =>
  mount(DocTab, { props: { doc: SAMPLE_DOC, ...props } })

describe('DocTab', () => {
  let wrapper: ReturnType<typeof mountTab>

  beforeEach(() => {
    window.localStorage.clear()
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  it('starts in form mode and renders frontmatter fields plus body', () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="doc-tab-form"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="doc-tab-raw"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="doc-tab-view"]').exists()).toBe(false)
    expect(
      (wrapper.find('[data-testid="schema-field-name"]').element as HTMLInputElement).value,
    ).toBe('BatchProcessing')
    expect(
      (wrapper.find('[data-testid="schema-field-description"]').element as HTMLInputElement).value,
    ).toBe('Sums values')
    expect(wrapper.find('[data-testid="markdown-body-editor"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="md-heading-text-0"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="md-paragraph-1"]').exists()).toBe(true)
  })

  it('emits update:doc with serialized frontmatter when a field changes', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="schema-field-name"]').setValue('NewName')

    const emitted = wrapper.emitted('update:doc')?.at(-1)?.[0] as string
    expect(emitted).toContain('name: NewName')
    expect(emitted).toContain('description: Sums values')
    // The `# <name>` heading follows the frontmatter rename.
    expect(emitted).toContain('# NewName')
  })

  it('emits update:doc with frontmatter preserved when the body changes', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="md-paragraph-1"]').setValue('# New body')

    const emitted = wrapper.emitted('update:doc')?.at(-1)?.[0] as string
    expect(emitted).toContain('name: BatchProcessing')
    expect(emitted).toContain('# New body')
  })

  it('adds a frontmatter block when editing a doc that has none', async () => {
    wrapper = mountTab({ doc: '# Title\n' })

    await wrapper.find('[data-testid="schema-field-name"]').setValue('MyDsl')

    expect(wrapper.emitted('update:doc')?.at(-1)?.[0]).toContain('name: MyDsl')
  })

  describe('new doc creation', () => {
    it('seeds a blank doc with the name frontmatter and a "# " body section', () => {
      wrapper = mountTab({ doc: '', name: 'LoanDisbursement' })

      expect(
        (wrapper.find('[data-testid="schema-field-name"]').element as HTMLInputElement).value,
      ).toBe('LoanDisbursement')
      expect(
        (wrapper.find('[data-testid="md-heading-text-0"]').element as HTMLInputElement).value,
      ).toBe('LoanDisbursement')
    })

    it('seeds the raw editor with the yaml header and heading', async () => {
      wrapper = mountTab({ doc: '', name: 'LoanDisbursement' })

      await wrapper.find('[data-testid="doc-tab-mode-raw"]').trigger('click')

      const value = (
        wrapper.find('[data-testid="doc-tab-textarea"]').element as HTMLTextAreaElement
      ).value
      expect(value).toBe(
        '---\nname: LoanDisbursement\ndescription: ""\n---\n\n# LoanDisbursement\n',
      )
    })

    it('renders the name in the view header and as the body heading', async () => {
      wrapper = mountTab({ doc: '', name: 'LoanDisbursement' })

      await wrapper.find('[data-testid="doc-tab-mode-view"]').trigger('click')

      expect(wrapper.find('[data-testid="doc-tab-view-name"]').text()).toBe('LoanDisbursement')
      expect(wrapper.find('[data-testid="doc-tab-view-body"]').html()).toContain(
        '<h1>LoanDisbursement</h1>',
      )
    })

    it('does not overwrite an existing doc when a name is supplied', () => {
      wrapper = mountTab({ doc: SAMPLE_DOC, name: 'OtherName' })

      expect(
        (wrapper.find('[data-testid="schema-field-name"]').element as HTMLInputElement).value,
      ).toBe('BatchProcessing')
    })

    it('leaves the doc blank when no name is supplied', () => {
      wrapper = mountTab({ doc: '' })

      expect(
        (wrapper.find('[data-testid="schema-field-name"]').element as HTMLInputElement).value,
      ).toBe('')
      expect(wrapper.find('[data-testid="md-heading-text-0"]').exists()).toBe(false)
    })

    it('retitles the body heading when the frontmatter name changes', async () => {
      wrapper = mountTab({ doc: '', name: 'LoanDisbursement' })

      await wrapper.find('[data-testid="schema-field-name"]').setValue('Renamed')

      const emitted = wrapper.emitted('update:doc')?.at(-1)?.[0] as string
      expect(emitted).toContain('name: Renamed')
      expect(emitted).toContain('# Renamed')
    })

    it('keeps a hand-written heading when the frontmatter name changes', async () => {
      wrapper = mountTab({ doc: '---\nname: Old\n---\n\n# Custom title\n\ntext\n' })

      await wrapper.find('[data-testid="schema-field-name"]').setValue('New')

      const emitted = wrapper.emitted('update:doc')?.at(-1)?.[0] as string
      expect(emitted).toContain('name: New')
      expect(emitted).toContain('# Custom title')
    })

    it('adopts a doc that arrives from the parent after mount', async () => {
      wrapper = mountTab({ doc: '', name: 'LoanDisbursement' })
      expect(
        (wrapper.find('[data-testid="schema-field-name"]').element as HTMLInputElement).value,
      ).toBe('LoanDisbursement')

      await wrapper.setProps({ doc: SAMPLE_DOC })

      expect(
        (wrapper.find('[data-testid="schema-field-name"]').element as HTMLInputElement).value,
      ).toBe('BatchProcessing')
      expect(wrapper.find('[data-testid="doc-tab-dirty-indicator"]').exists()).toBe(false)
    })

    it('does not discard local edits when the parent pushes a doc', async () => {
      wrapper = mountTab()
      await wrapper.find('[data-testid="md-paragraph-1"]').setValue('# local edit')

      await wrapper.setProps({ doc: SAMPLE_DOC })

      expect(wrapper.emitted('update:doc')?.at(-1)?.[0]).toContain('# local edit')
      expect(wrapper.find('[data-testid="doc-tab-dirty-indicator"]').exists()).toBe(true)
    })

    it('ignores its own emission echoed back as a prop', async () => {
      wrapper = mountTab()

      await wrapper.find('[data-testid="md-paragraph-1"]').setValue('# local edit')
      await wrapper.setProps({ doc: wrapper.emitted('update:doc')?.at(-1)?.[0] as string })

      expect(wrapper.find('[data-testid="doc-tab-dirty-indicator"]').exists()).toBe(true)
    })
  })

  it('switches to raw mode with a markdown editor and back', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="doc-tab-mode-raw"]').trigger('click')

    const raw = wrapper.find('[data-testid="doc-tab-raw"]')
    expect(raw.exists()).toBe(true)
    expect(wrapper.find('[data-testid="doc-tab-form"]').exists()).toBe(false)
    const textarea = wrapper.find('[data-testid="doc-tab-textarea"]')
    expect((textarea.element as HTMLTextAreaElement).value).toBe(SAMPLE_DOC)
    expect(textarea.attributes('placeholder')).toBe('Write documentation here...')

    await wrapper.find('[data-testid="doc-tab-mode-form"]').trigger('click')
    expect(wrapper.find('[data-testid="doc-tab-form"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="doc-tab-raw"]').exists()).toBe(false)
  })

  it('emits update:doc when typing in the raw editor', async () => {
    wrapper = mountTab()
    await wrapper.find('[data-testid="doc-tab-mode-raw"]').trigger('click')

    await wrapper.find('[data-testid="doc-tab-textarea"]').setValue('# raw edit\n')

    expect(wrapper.emitted('update:doc')?.at(-1)?.[0]).toBe('# raw edit\n')
  })

  it('parses raw edits when switching back to form mode', async () => {
    wrapper = mountTab()
    await wrapper.find('[data-testid="doc-tab-mode-raw"]').trigger('click')
    await wrapper
      .find('[data-testid="doc-tab-textarea"]')
      .setValue('---\nname: RawName\ndescription: from raw\n---\n\n# Body\n')

    await wrapper.find('[data-testid="doc-tab-mode-form"]').trigger('click')

    expect(
      (wrapper.find('[data-testid="schema-field-name"]').element as HTMLInputElement).value,
    ).toBe('RawName')
    expect(wrapper.find('[data-testid="md-heading-text-0"]').exists()).toBe(true)
  })

  it('persists the selected mode', async () => {
    wrapper = mountTab()
    await wrapper.find('[data-testid="doc-tab-mode-raw"]').trigger('click')
    wrapper.unmount()

    expect(window.localStorage.getItem('cbs-nova:doc-tab:mode')).toBe(JSON.stringify('raw'))

    wrapper = mountTab()
    expect(wrapper.find('[data-testid="doc-tab-raw"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="doc-tab-form"]').exists()).toBe(false)
  })

  it('supports the view mode and renders markdown html', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="doc-tab-mode-view"]').trigger('click')

    const view = wrapper.find('[data-testid="doc-tab-view"]')
    expect(view.exists()).toBe(true)
    expect(wrapper.find('[data-testid="doc-tab-form"]').exists()).toBe(false)
    const body = wrapper.find('[data-testid="doc-tab-view-body"]')
    expect(body.exists()).toBe(true)
    expect(body.html()).toContain('<h1')
    expect(body.html()).toContain('BatchProcessing')
    expect(body.html()).toContain('<p>')
    expect(wrapper.find('[data-testid="doc-tab-view-name"]').text()).toBe('BatchProcessing')
    expect(wrapper.find('[data-testid="doc-tab-view-description"]').text()).toBe('Sums values')
  })

  it('renders the footer in view mode with word/char counts', async () => {
    wrapper = mountTab()
    await wrapper.find('[data-testid="doc-tab-mode-view"]').trigger('click')

    const footer = wrapper.find('[data-testid="doc-tab-view-footer"]')
    expect(footer.exists()).toBe(true)
    expect(footer.text()).toMatch(/words/)
    expect(footer.text()).toMatch(/chars/)
  })

  it('does not render the frontmatter header in view mode when missing', async () => {
    wrapper = mountTab({ doc: '# Plain\n' })
    await wrapper.find('[data-testid="doc-tab-mode-view"]').trigger('click')

    expect(wrapper.find('[data-testid="doc-tab-view-header"]').exists()).toBe(false)
  })

  it('renders the body editor with insert controls in form mode', async () => {
    wrapper = mountTab()

    const toolbar = wrapper.find('[data-testid="markdown-body-toolbar"]')
    expect(toolbar.exists()).toBe(true)
    expect(wrapper.find('[data-testid="md-insert-h1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="md-insert-h2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="md-insert-ul"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="md-insert-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="md-marker-bold"]').exists()).toBe(true)
  })

  it('inserts a heading block from the toolbar and emits update:doc', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="md-insert-h2"]').trigger('click')

    const emitted = wrapper.emitted('update:doc')?.at(-1)?.[0] as string
    expect(emitted).toContain('Some text')
    expect(emitted.split('\n').some((line) => line.startsWith('## '))).toBe(true)
  })

  it('removes a block and emits the updated body', async () => {
    wrapper = mountTab({ doc: '## A\n\nkeep me\n' })

    await wrapper.find('[data-testid="md-remove-0"]').trigger('click')

    const emitted = wrapper.emitted('update:doc')?.at(-1)?.[0] as string
    expect(emitted).not.toContain('## A')
    expect(emitted).toContain('keep me')
  })

  it('moves blocks up and down and emits the updated body', async () => {
    wrapper = mountTab({ doc: '## A\n\nkeep me\n' })

    await wrapper.find('[data-testid="md-move-down-0"]').trigger('click')

    const emitted = wrapper.emitted('update:doc')?.at(-1)?.[0] as string
    const idxA = emitted.indexOf('## A')
    const idxKeep = emitted.indexOf('keep me')
    expect(idxA).toBeGreaterThan(-1)
    expect(idxKeep).toBeGreaterThan(-1)
    expect(idxA).toBeGreaterThan(idxKeep)
  })

  it('renders the doc-tab-header with a Save button', () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="doc-tab-header"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="doc-tab-save"]').exists()).toBe(true)
  })

  it('renders Refresh and Validate buttons in the header', () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="doc-tab-refresh"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="doc-tab-validate"]').exists()).toBe(true)
  })

  it('places Save to the right of Refresh and Validate', () => {
    wrapper = mountTab()

    const refresh = wrapper.find('[data-testid="doc-tab-refresh"]').element as HTMLElement
    const validate = wrapper.find('[data-testid="doc-tab-validate"]').element as HTMLElement
    const save = wrapper.find('[data-testid="doc-tab-save"]').element as HTMLElement
    const compare = (a: HTMLElement, b: HTMLElement) =>
      (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0
    expect(compare(refresh, save)).toBe(true)
    expect(compare(validate, save)).toBe(true)
  })

  it('disables Refresh and Validate when no callbacks are provided', () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="doc-tab-refresh"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="doc-tab-validate"]').attributes('disabled')).toBeDefined()
  })

  it('invokes the refresh callback when Refresh is clicked', async () => {
    const refresh = vi.fn(async () => undefined)
    wrapper = mountTab({ refresh })

    expect(wrapper.find('[data-testid="doc-tab-refresh"]').attributes('disabled')).toBeUndefined()

    await wrapper.find('[data-testid="doc-tab-refresh"]').trigger('click')

    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('invokes the validate callback when Validate is clicked', async () => {
    const validate = vi.fn(async () => undefined)
    wrapper = mountTab({ validate })

    await wrapper.find('[data-testid="doc-tab-validate"]').trigger('click')

    expect(validate).toHaveBeenCalledTimes(1)
  })

  it('disables Refresh and Validate while busy', async () => {
    const refresh = vi.fn(async () => undefined)
    const validate = vi.fn(async () => undefined)
    wrapper = mountTab({ refresh, validate })

    await wrapper.setProps({ busy: true })

    expect(wrapper.find('[data-testid="doc-tab-refresh"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="doc-tab-validate"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-testid="doc-tab-refresh"]').trigger('click')
    await wrapper.find('[data-testid="doc-tab-validate"]').trigger('click')

    expect(refresh).not.toHaveBeenCalled()
    expect(validate).not.toHaveBeenCalled()
  })

  it('hides Refresh and Validate when readOnly', () => {
    wrapper = mountTab({
      readOnly: true,
      refresh: vi.fn(),
      validate: vi.fn(),
    })

    expect(wrapper.find('[data-testid="doc-tab-refresh"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="doc-tab-validate"]').exists()).toBe(false)
  })

  it('does not duplicate the Save button into the footer', () => {
    wrapper = mountTab()

    expect(wrapper.findAll('[data-testid="doc-tab-save"]')).toHaveLength(1)
    const footer = wrapper.find('[data-testid="doc-tab-footer"]')
    expect(footer.exists()).toBe(true)
    expect(footer.find('[data-testid="doc-tab-save"]').exists()).toBe(false)
  })

  it('hides both header and footer in readOnly mode', () => {
    wrapper = mountTab({ readOnly: true })

    expect(wrapper.find('[data-testid="doc-tab-header"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="doc-tab-footer"]').exists()).toBe(false)
  })

  it('applies bold marker to the last paragraph and emits update:doc', async () => {
    wrapper = mountTab({ doc: 'hello world\n' })

    await wrapper.find('[data-testid="md-marker-bold"]').trigger('click')

    const emitted = wrapper.emitted('update:doc')?.at(-1)?.[0] as string
    expect(emitted).toContain('**bold text**')
    expect(emitted).toContain('hello world')
  })

  it('shows the dirty indicator after editing', async () => {
    wrapper = mountTab()

    expect(wrapper.find('[data-testid="doc-tab-dirty-indicator"]').exists()).toBe(false)

    await wrapper.find('[data-testid="schema-field-name"]').setValue('Changed')

    expect(wrapper.find('[data-testid="doc-tab-dirty-indicator"]').exists()).toBe(true)
  })

  it('emits save with the current doc when Save is clicked', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="md-paragraph-1"]').setValue('# changed')
    await wrapper.find('[data-testid="doc-tab-save"]').trigger('click')

    const saved = wrapper.emitted('save')?.at(-1)?.[0] as string
    expect(saved).toContain('# changed')
    expect(saved).toContain('name: BatchProcessing')
    expect(wrapper.find('[data-testid="doc-tab-dirty-indicator"]').exists()).toBe(false)
  })

  it('hides the footer and marks fields read-only in readOnly mode', () => {
    wrapper = mountTab({ readOnly: true })

    expect(wrapper.find('[data-testid="doc-tab-footer"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="schema-field-name"]').attributes('disabled')).toBeDefined()
  })

  it('marks the raw editor read-only in readOnly mode', () => {
    window.localStorage.setItem('cbs-nova:doc-tab:mode', JSON.stringify('raw'))
    wrapper = mountTab({ readOnly: true })

    const textarea = wrapper.find('[data-testid="doc-tab-textarea"]')
    expect(textarea.attributes('readonly')).toBeDefined()
    expect(textarea.attributes('placeholder')).toBe('No documentation available')
  })

  it('renders the save status chip', async () => {
    wrapper = mountTab({ saveStatus: 'error' })

    const status = wrapper.find('[data-testid="doc-tab-save-status"]')
    expect(status.text()).toContain('Save failed')
    expect(status.classes()).toEqual(expect.arrayContaining(['bg-red-50']))

    await wrapper.find('[data-testid="doc-tab-save-retry"]').trigger('click')
    expect(wrapper.emitted('save')).toEqual([[SAMPLE_DOC]])
  })

  it('saves via the global dsl:save event when dirty', async () => {
    wrapper = mountTab()

    await wrapper.find('[data-testid="md-paragraph-1"]').setValue('# changed')
    window.dispatchEvent(new Event('dsl:save'))

    const saved = wrapper.emitted('save')?.at(-1)?.[0] as string
    expect(saved).toContain('# changed')
  })

  describe('autosave dropdown', () => {
    const openDropdown = async (wrapper: ReturnType<typeof mountTab>) => {
      await wrapper.find('[data-testid="doc-tab-save-dropdown-toggle"]').trigger('click')
    }

    it('renders the dropdown toggle and opens the menu', async () => {
      wrapper = mountTab()

      expect(wrapper.find('[data-testid="doc-tab-save-dropdown-toggle"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="doc-tab-save-dropdown"]').exists()).toBe(false)

      await openDropdown(wrapper)

      const dropdown = wrapper.find('[data-testid="doc-tab-save-dropdown"]')
      expect(dropdown.exists()).toBe(true)
      const options = dropdown
        .findAll('[data-testid^="doc-tab-autosave-"]')
        .map((node) => node.attributes('data-testid'))
      expect(options).toEqual([
        'doc-tab-autosave-off',
        'doc-tab-autosave-5s',
        'doc-tab-autosave-30s',
        'doc-tab-autosave-1min',
        'doc-tab-autosave-blur',
      ])
    })

    it('persists the selected autosave mode and marks it active', async () => {
      wrapper = mountTab()
      await openDropdown(wrapper)
      await wrapper.find('[data-testid="doc-tab-autosave-1min"]').trigger('click')
      wrapper.unmount()

      expect(window.localStorage.getItem('cbs-nova:doc-tab:autosave-mode')).toBe(
        JSON.stringify('1min'),
      )

      wrapper = mountTab()
      await openDropdown(wrapper)
      expect(wrapper.find('[data-testid="doc-tab-autosave-1min"]').classes()).toEqual(
        expect.arrayContaining(['font-semibold']),
      )
    })

    it('autosaves on blur when the blur mode is picked', async () => {
      wrapper = mountTab()

      await openDropdown(wrapper)
      await wrapper.find('[data-testid="doc-tab-autosave-blur"]').trigger('click')
      await wrapper.find('[data-testid="md-paragraph-1"]').setValue('# changed')
      await wrapper.find('[data-testid="md-paragraph-1"]').trigger('focusout')

      const saved = wrapper.emitted('save')?.at(-1)?.[0] as string
      expect(saved).toContain('# changed')
    })

    it('autosaves on the picked interval when dirty', async () => {
      vi.useFakeTimers()
      wrapper = mountTab()

      await openDropdown(wrapper)
      await wrapper.find('[data-testid="doc-tab-autosave-5s"]').trigger('click')
      await wrapper.find('[data-testid="md-paragraph-1"]').setValue('# changed')

      vi.advanceTimersByTime(5000)
      expect(wrapper.emitted('save')).toHaveLength(1)

      vi.advanceTimersByTime(5000)
      expect(wrapper.emitted('save')).toHaveLength(1)
    })
  })

  describe('hotkeys', () => {
    function dispatchWindowKey(opts: KeyboardEventInit & { key: string }) {
      window.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, ...opts }))
    }

    it('triggers refresh on Ctrl+Shift+R', async () => {
      const refresh = vi.fn(async () => undefined)
      wrapper = mountTab({ refresh })

      dispatchWindowKey({ key: 'r', ctrlKey: true, shiftKey: true })

      expect(refresh).toHaveBeenCalledTimes(1)
    })

    it('does not trigger refresh on plain Ctrl+R', async () => {
      const refresh = vi.fn(async () => undefined)
      wrapper = mountTab({ refresh })

      dispatchWindowKey({ key: 'r', ctrlKey: true })

      expect(refresh).not.toHaveBeenCalled()
    })

    it('triggers validate on Ctrl+Enter', async () => {
      const validate = vi.fn(async () => undefined)
      wrapper = mountTab({ validate })

      dispatchWindowKey({ key: 'Enter', ctrlKey: true })

      expect(validate).toHaveBeenCalledTimes(1)
    })

    it('does not trigger hotkeys when readOnly', async () => {
      const refresh = vi.fn()
      const validate = vi.fn()
      wrapper = mountTab({ readOnly: true, refresh, validate })

      dispatchWindowKey({ key: 'r', ctrlKey: true, shiftKey: true })
      dispatchWindowKey({ key: 'Enter', ctrlKey: true })

      expect(refresh).not.toHaveBeenCalled()
      expect(validate).not.toHaveBeenCalled()
    })

    it('suppresses hotkeys while busy', async () => {
      const refresh = vi.fn(async () => undefined)
      const validate = vi.fn(async () => undefined)
      wrapper = mountTab({ refresh, validate })

      await wrapper.setProps({ busy: true })

      dispatchWindowKey({ key: 'r', ctrlKey: true, shiftKey: true })
      dispatchWindowKey({ key: 'Enter', ctrlKey: true })

      expect(refresh).not.toHaveBeenCalled()
      expect(validate).not.toHaveBeenCalled()
    })
  })
})
