import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  __resetConstructSchemaCache,
  DSL_SCHEMA_FETCH_KEY,
} from '../../composables/useConstructSchema'
import {
  __resetExplainHistoryForTests,
  EXPLAIN_HISTORY_STORAGE_KEY,
  EXPLAIN_HISTORY_STORAGE_NAMESPACE,
} from '../../composables/usePreviewHistory'
import type { RunnerOutput } from '../../types/runner'
import ExplainTab from '../dsl/ExplainTab.vue'

function mountTab(
  props: Record<string, unknown> = {},
  explain: (
    name: string,
    body: unknown,
    metadata?: Record<string, unknown>,
  ) => RunnerOutput | Promise<RunnerOutput> = vi
    .fn<
      (name: string, body: unknown, metadata?: Record<string, unknown>) => Promise<RunnerOutput>
    >()
    .mockResolvedValue({
      description: 'explain ok',
      markdown: 'graph TD',
    } as unknown as RunnerOutput as unknown as RunnerOutput),
  fetchMock = vi.fn().mockResolvedValue({}),
) {
  return mount(ExplainTab, {
    props: { name: 'demo', explain, ...props },
    global: {
      stubs: {
        RunResultPanel: {
          name: 'RunResultPanel',
          template: `\u003csection data-testid="runner-result-panel"\u003e
              \u003cheader\u003e
                \u003cspan data-testid="result-title"\u003eResult \u00b7 {{ endpoint ?? 'explain' }}\u003c/span\u003e
                \u003cspan data-testid="result-status"\u003e{{ status === 'success' ? 'done' : status }}\u003c/span\u003e
              \u003c/header\u003e
              \u003cdiv data-testid="runner-result-tab"\u003e{{ output !== undefined ? JSON.stringify(output) : 'No result yet.' }}\u003c/div\u003e
            \u003c/section\u003e`,
          props: ['output', 'status', 'endpoint', 'name', 'type', 'history'],
        },
        ResultTab: {
          template:
            '\u003cdiv data-testid="runner-result-tab"\u003e{{ result !== undefined ? JSON.stringify(result) : "No result yet." }}\u003c/div\u003e',
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

describe('ExplainTab', () => {
  beforeEach(() => {
    __resetConstructSchemaCache()
    __resetExplainHistoryForTests()
    window.localStorage.clear()
  })

  afterEach(() => {
    vi.resetModules()
  })

  it('renders both input and result panels', () => {
    const wrapper = mountTab()
    expect(wrapper.text()).toContain('Input')
    expect(wrapper.text()).toContain('Result \u00b7 explain')
  })

  it('disables Run when JSON is invalid', async () => {
    const wrapper = mountTab()
    const textarea = wrapper.find('[data-testid="json-textarea"]')
    await textarea.setValue('{ bad json')
    const runBtn = wrapper.findAll('button').find((b) => b.text() === 'Run')!
    expect(runBtn.attributes('disabled')).toBeDefined()
  })

  it('calls the explain prop and shows done on success', async () => {
    const explain = vi.fn().mockResolvedValue({
      description: 'explain ok',
      markdown: 'graph TD',
    } as unknown as RunnerOutput)
    const wrapper = mountTab({}, explain)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{"a":1}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    expect(explain).toHaveBeenCalledWith('demo', { a: 1 }, { startedFrom: 'workbench' })
    expect(wrapper.text()).toContain('done')
    const resultText = wrapper.find('[data-testid="runner-result-tab"]').text()
    expect(resultText).toContain('explain ok')
    expect(resultText).toContain('mermaidDiagram')
  })

  it('normalizes backend mermaid field to mermaidDiagram', async () => {
    const explain = vi
      .fn()
      .mockResolvedValue({ description: 'd', markdown: 'g' } as unknown as RunnerOutput)
    const wrapper = mountTab({}, explain)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    const resultText = wrapper.find('[data-testid="runner-result-tab"]').text()
    expect(resultText).toContain('"mermaidDiagram":"g"')
  })

  it('passes the backend ExplainReport through as typed explainReport data', async () => {
    const report = {
      name: 'demo',
      description: 'short summary',
      markdown: '# Long-form body',
      children: [{ name: 'child', description: 'c', markdown: 'x', children: [] }],
    }
    const explain = vi.fn().mockResolvedValue(report)
    const wrapper = mountTab({}, explain)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    const output = JSON.parse(wrapper.find('[data-testid="runner-result-tab"]').text())
    expect(output.explainReport).toEqual(report)
    expect(output.result).toBeUndefined()
  })

  it('surfaces backend errors on failure', async () => {
    const explain = vi.fn().mockRejectedValue({
      data: { errors: [{ message: 'explain failed' }] },
      statusMessage: 'Unprocessable Entity',
    })

    const wrapper = mountTab({}, explain)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('explain failed')
    expect(wrapper.text()).toContain('failed')
  })

  it('normalizes BFF error envelope ({message, code}) into errors[] for the result panel', async () => {
    const explain = vi.fn().mockRejectedValue({
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

    const wrapper = mountTab({}, explain)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('LinkedHashMap cannot be cast')
    expect(wrapper.text()).toContain('failed')
    const resultText = wrapper.find('[data-testid="runner-result-tab"]').text()
    expect(resultText).toContain('UNPROCESSABLE_ENTITY')
  })

  it('falls back to statusMessage when BFF envelope has no message', async () => {
    const explain = vi.fn().mockRejectedValue({
      data: { code: 'BACKEND_TIMEOUT', details: null, diagnostics: null },
      statusMessage: 'Backend request timed out',
    })

    const wrapper = mountTab({}, explain)
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
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/processes/demo/schema')
    expect(wrapper.find('[data-testid="mode-form"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="mode-json"]').exists()).toBe(true)
  })

  it('switches to Form mode and submits the form value', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)

    const explain = vi.fn().mockResolvedValue({ description: 'ok' } as unknown as RunnerOutput)
    const wrapper = mountTab({ type: 'Process' }, explain, fetchMock)
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

    expect(explain).toHaveBeenLastCalledWith(
      'demo',
      { name: 'alice', count: 3 },
      { startedFrom: 'workbench' },
    )
  })

  it('records a successful run in explain history', async () => {
    const explain = vi.fn().mockResolvedValue({ description: 'ok' } as unknown as RunnerOutput)
    const wrapper = mountTab({}, explain)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{"a":1}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    const stored = JSON.parse(
      window.localStorage.getItem(
        `${EXPLAIN_HISTORY_STORAGE_NAMESPACE}:${EXPLAIN_HISTORY_STORAGE_KEY}`,
      ) ?? '[]',
    )
    expect(stored).toHaveLength(1)
    expect(stored[0]).toMatchObject({
      name: 'demo',
      payload: { a: 1 },
      status: 'success',
    })
    expect(stored[0].id).toBeTruthy()
    expect(stored[0].startedAt).toBeTruthy()
  })

  it('records a failed run in explain history', async () => {
    const explain = vi.fn().mockRejectedValue({
      data: { errors: [{ message: 'explain failed' }] },
      statusMessage: 'Unprocessable Entity',
    })

    const wrapper = mountTab({}, explain)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{"a":1}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()

    const stored = JSON.parse(
      window.localStorage.getItem(
        `${EXPLAIN_HISTORY_STORAGE_NAMESPACE}:${EXPLAIN_HISTORY_STORAGE_KEY}`,
      ) ?? '[]',
    )
    expect(stored).toHaveLength(1)
    expect(stored[0].status).toBe('failed')
    expect(stored[0].output.errors[0].message).toBe('explain failed')
  })

  it('reruns a stored history payload through the explain function', async () => {
    const explain = vi.fn().mockResolvedValue({ description: 'ok' } as unknown as RunnerOutput)
    const wrapper = mountTab({}, explain)
    await wrapper.find('[data-testid="json-textarea"]').setValue('{"a":1}')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Run')!
      .trigger('click')
    await flushPromises()
    explain.mockClear()

    wrapper.findComponent({ name: 'RunResultPanel' }).vm.$emit('rerun', { b: 2 })
    await flushPromises()

    expect(explain).toHaveBeenCalledWith('demo', { b: 2 }, { startedFrom: 'workbench' })
    expect(wrapper.text()).toContain('done')

    const textarea = wrapper.find('[data-testid="json-textarea"]')
    expect((textarea.element as HTMLTextAreaElement).value).toContain('"b": 2')
  })
})
