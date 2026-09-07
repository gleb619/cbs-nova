import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PreviewTab from '../dsl/PreviewTab.vue'
import PreviewInputPanel from '../dsl/PreviewInputPanel.vue'
import { __resetConstructSchemaCache } from '../../composables/useConstructSchema'

function mountTab(props: Record<string, unknown> = {}) {
  return mount(PreviewTab, {
    props: { name: 'demo', ...props },
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
          props: ['output', 'status', 'endpoint'],
        },
        ResultTab: {
          template: '<div data-testid="runner-result-tab">{{ result !== undefined ? JSON.stringify(result) : "No result yet." }}</div>',
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
}

describe('PreviewTab', () => {
  beforeEach(() => {
    __resetConstructSchemaCache()
    vi.stubGlobal('$fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
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

  it('calls $fetch and shows done on success', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ result: { ok: true } })
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountTab()
    await wrapper.find('[data-testid="json-textarea"]').setValue('{"a":1}')
    await wrapper.findAll('button').find((b) => b.text() === 'Run')!.trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/dsl/preview/demo',
      expect.objectContaining({ method: 'POST', body: { body: { a: 1 }, metadata: { startedFrom: 'workbench' } } }),
    )
    expect(wrapper.text()).toContain('done')
  })

  it('surfaces backend errors on failure', async () => {
    const fetchMock = vi.fn().mockRejectedValue({
      data: { errors: [{ message: 'preview failed' }] },
      statusMessage: 'Unprocessable Entity',
    })
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountTab()
    await wrapper.find('[data-testid="json-textarea"]').setValue('{}')
    await wrapper.findAll('button').find((b) => b.text() === 'Run')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('preview failed')
    expect(wrapper.text()).toContain('failed')
  })

  it('uses the explain endpoint when configured', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ result: { ok: true } })
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountTab({ endpoint: 'explain' })
    await wrapper.find('[data-testid="json-textarea"]').setValue('{}')
    await wrapper.findAll('button').find((b) => b.text() === 'Run')!.trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/dsl/explain/demo',
      expect.any(Object),
    )
  })

  it('fetches schema when type is Process and renders Form toggle', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountTab({ type: 'Process' })
    await flushPromises()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/processes/demo')
    expect(wrapper.find('[data-testid="mode-form"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="mode-json"]').exists()).toBe(true)
  })

  it('switches to Form mode and submits the form value', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountTab({ type: 'Process' })
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-testid="schema-field-name"]').setValue('alice')
    await wrapper.find('[data-testid="schema-field-count"]').setValue('3')
    await flushPromises()

    await wrapper.findAll('button').find((b) => b.text() === 'Run')!.trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/v1/dsl/preview/demo',
      expect.objectContaining({ method: 'POST', body: { body: { name: 'alice', count: 3 }, metadata: { startedFrom: 'workbench' } } }),
    )
  })

  it('hides Form toggle and locks JSON when schema fetch fails', async () => {
    const fetchMock = vi.fn().mockRejectedValue({ statusMessage: 'Server error' })
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountTab({ type: 'Process' })
    // wait for watch + microtasks
    for (let i = 0; i < 50; i++) {
      await flushPromises()
    }

    // Error path forces JSON-only; no toggle rendered
    expect(wrapper.find('[data-testid="mode-form"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="json-textarea"]').exists()).toBe(true)
  })
})
