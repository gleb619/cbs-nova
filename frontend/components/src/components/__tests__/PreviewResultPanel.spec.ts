import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { __resetConstructSchemaCache } from '../../composables/useConstructSchema'
import PreviewResultPanel from '../dsl/PreviewResultPanel.vue'
import SchemaFormField from '../dsl/SchemaFormField.vue'

function mountPanel(props: Record<string, unknown> = {}) {
  return mount(PreviewResultPanel, {
    props: {
      name: 'demo',
      output: null,
      status: 'idle',
      ...props,
    },
    global: {
      components: { SchemaFormField },
      stubs: {
        ResultTab: {
          name: 'ResultTab',
          template: '<pre data-testid="runner-result-tab">{{ JSON.stringify(result) }}</pre>',
          props: ['result'],
        },
      },
    },
  })
}

const schemaResponse = {
  inputSchema: {
    type: 'object',
    properties: {
      name: { type: 'string' },
      count: { type: 'number' },
    },
    required: ['name'],
  },
  outputSchema: {
    type: 'object',
    properties: {
      result: { type: 'string' },
    },
  },
}

describe('PreviewResultPanel', () => {
  beforeEach(() => {
    __resetConstructSchemaCache()
    vi.stubGlobal('$fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders JSON mode by default and exposes a history button in the header', () => {
    const wrapper = mountPanel({ output: { result: { ok: true } }, status: 'success' })
    expect(wrapper.find('[data-testid="mode-json"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="mode-form"]').exists()).toBe(true)
    expect(wrapper.find('header').find('[data-testid="history-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="runner-result-tab"]').text()).toContain('{"ok":true}')
  })

  it('emits history when the header history button is clicked', async () => {
    const wrapper = mountPanel({ output: { result: { ok: true } }, status: 'success' })
    await wrapper.find('[data-testid="history-button"]').trigger('click')
    expect(wrapper.emitted('history')).toBeTruthy()
  })

  it('renders output type name from schema endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ...schemaResponse,
      outputType: 'BatchOut',
    })
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountPanel({ type: 'Process' })
    await flushPromises()

    expect(wrapper.text()).toContain('BatchOut')
  })

  it('renders backend errors on the JSON tab', () => {
    const wrapper = mountPanel({
      output: { errors: [{ message: 'preview failed' }] },
      status: 'failed',
    })
    expect(wrapper.text()).toContain('preview failed')
    expect(wrapper.find('[data-testid="result-status"]').text()).toContain('Failed')
  })

  it('switches to Form mode and renders read-only form', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountPanel({ type: 'Process' })
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="schema-form"]').exists()).toBe(true)
    const field = wrapper.find('[data-testid="schema-field-result"]')
    expect(field.exists()).toBe(true)
    expect(field.attributes('disabled')).toBeDefined()
  })

  it('shows skeleton while schema is loading in Form mode', async () => {
    vi.stubGlobal('$fetch', () => new Promise(() => {}))

    const wrapper = mountPanel({ type: 'Process' })
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="schema-skeleton"]').exists()).toBe(true)
  })

  it('shows error state when schema fetch fails in Form mode', async () => {
    const fetchMock = vi.fn().mockRejectedValue({ statusMessage: 'Server error' })
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountPanel({ type: 'Process' })
    // wait for rejected promise to settle
    for (let i = 0; i < 20; i++) {
      await flushPromises()
    }

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Schema unavailable')
    expect(wrapper.text()).toContain('Server error')
  })

  it('shows empty state when output schema is missing in Form mode', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      inputSchema: schemaResponse.inputSchema,
      outputSchema: null,
    })
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountPanel({ type: 'Process' })
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Output schema unavailable')
  })

  it('resets to JSON mode when name changes', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountPanel({ type: 'Process' })
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="schema-form"]').exists()).toBe(true)

    await wrapper.setProps({ name: 'other' })
    await flushPromises()

    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="schema-form"]').exists()).toBe(false)
  })

  it('shows skeleton while result request is in flight', () => {
    const wrapper = mountPanel({ output: null, status: 'loading' })
    expect(wrapper.find('[data-testid="result-skeleton"]').exists()).toBe(true)
  })

  it('shows done status after success', () => {
    const wrapper = mountPanel({ output: { result: { ok: true } }, status: 'success' })
    const status = wrapper.find('[data-testid="result-status"]')
    expect(status.text()).toBe('Done')
    expect(status.classes()).toContain('text-success-600')
  })

  it('shows result JSON status by default', () => {
    const wrapper = mountPanel({ output: null, status: 'idle' })
    expect(wrapper.find('[data-testid="result-status"]').text()).toBe('Result JSON')
  })

  it('shows the format button in the footer', () => {
    const wrapper = mountPanel({ output: { result: { ok: true } }, status: 'success' })
    const footer = wrapper.find('footer')
    expect(footer.find('[data-testid="format-result"]').exists()).toBe(true)
  })

  it('emits formatted JSON when the format button is clicked', async () => {
    const wrapper = mountPanel({ output: { result: { ok: true } }, status: 'success' })
    await wrapper.find('[data-testid="format-result"]').trigger('click')
    await flushPromises()

    const emitted = wrapper.emitted('format')
    expect(emitted).toBeTruthy()
    expect(emitted?.[0]?.[0]).toBe('{\n  "ok": true\n}\n')
  })

  it('disables the format button when there is no result', () => {
    const wrapper = mountPanel({ output: null, status: 'idle' })
    expect(wrapper.find('[data-testid="format-result"]').attributes('disabled')).toBeDefined()
  })

  it('disables the format button in form mode', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountPanel({ type: 'Process' })
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="format-result"]').attributes('disabled')).toBeDefined()
  })

  it('disables the format button while loading', () => {
    const wrapper = mountPanel({ output: { result: { ok: true } }, status: 'loading' })
    expect(wrapper.find('[data-testid="format-result"]').attributes('disabled')).toBeDefined()
  })

  it('formats string results that contain compact JSON', async () => {
    const wrapper = mountPanel({
      output: { result: '{"ok":true}' },
      status: 'success',
    })
    await wrapper.find('[data-testid="format-result"]').trigger('click')
    await flushPromises()

    const emitted = wrapper.emitted('format')
    expect(emitted?.[0]?.[0]).toBe('{\n  "ok": true\n}\n')
  })
})
