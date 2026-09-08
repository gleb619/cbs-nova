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

  it('renders result tab by default', () => {
    const wrapper = mountPanel({ output: { result: { ok: true } }, status: 'success' })
    expect(wrapper.find('[data-testid="tab-result"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tab-output"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('done')
    expect(wrapper.find('[data-testid="runner-result-tab"]').text()).toContain('{"ok":true}')
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

  it('renders backend errors on the result tab', () => {
    const wrapper = mountPanel({
      output: { errors: [{ message: 'preview failed' }] },
      status: 'failed',
    })
    expect(wrapper.text()).toContain('preview failed')
    expect(wrapper.text()).toContain('failed')
  })

  it('switches to Output schema tab and renders read-only form', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountPanel({ type: 'Process' })
    await flushPromises()

    await wrapper.find('[data-testid="tab-output"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Output schema')
    expect(wrapper.find('[data-testid="schema-form"]').exists()).toBe(true)
    const field = wrapper.find('[data-testid="schema-field-result"]')
    expect(field.exists()).toBe(true)
    expect(field.attributes('disabled')).toBeDefined()
  })

  it('shows loading state on Output schema tab', async () => {
    vi.stubGlobal('$fetch', () => new Promise(() => {}))

    const wrapper = mountPanel({ type: 'Process' })
    await flushPromises()

    await wrapper.find('[data-testid="tab-output"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Loading schema')
  })

  it('shows error state when schema fetch fails', async () => {
    const fetchMock = vi.fn().mockRejectedValue({ statusMessage: 'Server error' })
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountPanel({ type: 'Process' })
    // wait for rejected promise to settle
    for (let i = 0; i < 20; i++) {
      await flushPromises()
    }

    await wrapper.find('[data-testid="tab-output"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Schema unavailable')
    expect(wrapper.text()).toContain('Server error')
  })

  it('shows empty state when output schema is missing', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      inputSchema: schemaResponse.inputSchema,
      outputSchema: null,
    })
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountPanel({ type: 'Process' })
    await flushPromises()

    await wrapper.find('[data-testid="tab-output"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Output schema unavailable')
  })

  it('resets to result tab when name changes', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountPanel({ type: 'Process' })
    await flushPromises()

    await wrapper.find('[data-testid="tab-output"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="schema-form"]').exists()).toBe(true)

    await wrapper.setProps({ name: 'other' })
    await flushPromises()

    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="schema-form"]').exists()).toBe(false)
  })
})
