import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DSL_SCHEMA_FETCH_KEY, __resetConstructSchemaCache } from '../../composables/useConstructSchema'
import PreviewTab from '../dsl/PreviewTab.vue'

function mountTab(
  props: Record<string, unknown> = {},
  preview: (name: string, body: unknown, metadata?: Record<string, unknown>) => unknown = vi
    .fn()
    .mockResolvedValue({ result: { ok: true } }),
  fetchMock = vi.fn().mockResolvedValue({}),
) {
  return mount(PreviewTab, {
    props: { name: 'demo', preview, ...props },
    global: {
      stubs: {
        PreviewResultPanel: {
          name: 'PreviewResultPanel',
          template: `<section data-testid="runner-result-panel">
              <header>
                <span data-testid="result-title">Result · {{ endpoint ?? 'preview' }}</span>
                <span data-testid="result-status">{{ status === 'success' ? 'done' : status }}</span>
              </header>
              <div data-testid="runner-result-tab">{{ output !== undefined ? JSON.stringify(output) : 'No result yet.' }}</div>
            </section>`,
          props: ['output', 'status', 'endpoint', 'name', 'type'],
        },
        ResultTab: {
          template:
            '<div data-testid="runner-result-tab">{{ result !== undefined ? JSON.stringify(result) : "No result yet." }}</div>',
          props: ['result'],
        },
      },
      provide: { [DSL_SCHEMA_FETCH_KEY as symbol]: fetchMock },
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

describe('PreviewTab', () => {
  beforeEach(() => {
    __resetConstructSchemaCache()
  })

  afterEach(() => {
    vi.resetModules()
  })

  it('renders both input and result panels', () => {
    const wrapper = mountTab()
    expect(wrapper.text()).toContain('Input')
    expect(wrapper.text()).toContain('Result · preview')
  })

  it('disables Run when JSON is invalid', async () => {
    const wrapper = mountTab()
    const textarea = wrapper.find('[data-testid="json-textarea"]')
    await textarea.setValue('{ bad json')
    const runBtn = wrapper.findAll('button').find((b) => b.text() === 'Run')!
    expect(runBtn.attributes('disabled')).toBeDefined()
  })

  it('calls the preview prop and shows done on success', async () => {
    const preview = vi.fn().mockResolvedValue({ result: { ok: true } })
    const wrapper = mountTab({}, preview)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{"a":1}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    expect(preview).toHaveBeenCalledWith('demo', { a: 1 }, { startedFrom: 'workbench' })
    expect(wrapper.text()).toContain('done')
  })

  it('surfaces backend errors on failure', async () => {
    const preview = vi.fn().mockRejectedValue({
      data: { errors: [{ message: 'preview failed' }] },
      statusMessage: 'Unprocessable Entity',
    })

    const wrapper = mountTab({}, preview)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('preview failed')
    expect(wrapper.text()).toContain('failed')
  })

  it('normalizes BFF error envelope ({message, code}) into errors[] for the result panel', async () => {
    const preview = vi.fn().mockRejectedValue({
      data: {
        message: 'LinkedHashMap cannot be cast to BatchModels$BatchIn',
        code: 'UNPROCESSABLE_ENTITY',
        details: { exceptionClass: 'ClassCastException' },
        diagnostics: null,
        backendUrl: 'http://localhost:8090',
        originalError: 'raw upstream message',
      },
      statusCode: 422,
      statusMessage: 'LinkedHashMap cannot be cast to BatchModels$BatchIn',
    })

    const wrapper = mountTab({}, preview)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    // Result panel must show the upstream error rather than "No result yet".
    expect(wrapper.text()).toContain('LinkedHashMap cannot be cast')
    expect(wrapper.text()).toContain('failed')
    // The normalized envelope should round-trip to the stubbed result panel
    // as a JSON string that contains the upstream code.
    const resultText = wrapper.find('[data-testid="runner-result-tab"]').text()
    expect(resultText).toContain('UNPROCESSABLE_ENTITY')
  })

  it('falls back to statusMessage when BFF envelope has no message', async () => {
    const preview = vi.fn().mockRejectedValue({
      data: { code: 'BACKEND_TIMEOUT', details: null, diagnostics: null },
      statusMessage: 'Backend request timed out',
    })

    const wrapper = mountTab({}, preview)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Backend request timed out')
    expect(wrapper.text()).toContain('failed')
  })

  it('fetches schema when type is Process and renders Form toggle', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)

    const wrapper = mountTab({ type: 'Process' }, undefined, fetchMock)
    await flushPromises()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/demo')
    expect(wrapper.find('[data-testid="mode-form"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="mode-json"]').exists()).toBe(true)
  })

  it('switches to Form mode and submits the form value', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)

    const preview = vi.fn().mockResolvedValue({ result: { ok: true } })
    const wrapper = mountTab({ type: 'Process' }, preview, fetchMock)
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-testid="schema-field-name"]').setValue('alice')
    await wrapper.find('[data-testid="schema-field-count"]').setValue('3')
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    expect(preview).toHaveBeenLastCalledWith(
      'demo',
      { name: 'alice', count: 3 },
      { startedFrom: 'workbench' },
    )
  })

  it('hides Form toggle and locks JSON when schema fetch fails', async () => {
    const fetchMock = vi.fn().mockRejectedValue({ statusMessage: 'Server error' })

    const wrapper = mountTab({ type: 'Process' }, undefined, fetchMock)
    // wait for watch + microtasks
    for (let i = 0; i < 50; i++) {
      await flushPromises()
    }

    // Error path forces JSON-only; no toggle rendered
    expect(wrapper.find('[data-testid="mode-form"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="json-textarea"]').exists()).toBe(true)
  })

  it('reacts to name change and fetches new schema', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)

    const wrapper = mountTab({ type: 'Process' }, undefined, fetchMock)
    await flushPromises()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/demo')

    fetchMock.mockClear()
    await wrapper.setProps({ name: 'other' })
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/other')
  })

  it('renders input type name from schema endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ...schemaResponse,
      inputType: 'BatchIn',
    })

    const wrapper = mountTab({ type: 'Process' }, undefined, fetchMock)
    await flushPromises()

    expect(wrapper.text()).toContain('BatchIn')
  })

  it('normalizes backend responses that use the output field', async () => {
    const preview = vi.fn().mockResolvedValue({
      output: { total: 42, summary: 'processed' },
      success: true,
    })

    const wrapper = mountTab({}, preview)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    const resultText = wrapper.find('[data-testid="runner-result-tab"]').text()
    expect(resultText).toContain('"result":{"total":42,"summary":"processed"}')
    expect(wrapper.text()).toContain('done')
  })
})
