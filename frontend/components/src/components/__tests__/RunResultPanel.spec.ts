import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  __resetConstructSchemaCache,
  DSL_SCHEMA_FETCH_KEY,
} from '../../composables/useConstructSchema'
import RunResultPanel from '../dsl/RunResultPanel.vue'
import SchemaFormField from '../dsl/SchemaFormField.vue'

function mountPanel(
  props: Record<string, unknown> = {},
  fetchMock = vi.fn().mockResolvedValue({}),
) {
  return mount(RunResultPanel, {
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
      provide: { [DSL_SCHEMA_FETCH_KEY as symbol]: fetchMock },
    },
  })
}

async function openHistory(wrapper: Awaited<ReturnType<typeof mountPanel>>) {
  await wrapper.find('[data-testid="history-button"]').trigger('click')
  await flushPromises()
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

describe('RunResultPanel', () => {
  beforeEach(() => {
    __resetConstructSchemaCache()
  })

  afterEach(() => {
    vi.resetModules()
  })

  it('renders JSON mode by default and exposes a history button in the header', () => {
    const wrapper = mountPanel({ output: { result: { ok: true } }, status: 'success' })
    expect(wrapper.find('[data-testid="mode-json"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="mode-form"]').exists()).toBe(true)
    expect(wrapper.find('header').find('[data-testid="history-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="runner-result-tab"]').text()).toContain('{"ok":true}')
  })

  it('toggles history mode when the header history button is clicked', async () => {
    const wrapper = mountPanel({ output: { result: { ok: true } }, status: 'success' })
    expect(wrapper.find('[data-testid="history-empty"]').exists()).toBe(false)

    await wrapper.find('[data-testid="history-button"]').trigger('click')
    expect(wrapper.find('[data-testid="history-empty"]').exists()).toBe(true)
    expect(wrapper.emitted('history')).toBeFalsy()

    await wrapper.find('[data-testid="history-button"]').trigger('click')
    expect(wrapper.find('[data-testid="history-empty"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(true)
  })

  it('lists history entries with status, time and payload summary', async () => {
    const history = [
      {
        id: '1',
        name: 'demo',
        payload: { a: 1 },
        output: { result: { ok: true } },
        status: 'success',
        startedAt: '2026-09-10T10:00:00.000Z',
      },
      {
        id: '2',
        name: 'demo',
        payload: { b: 2 },
        output: { errors: [{ message: 'boom' }] },
        status: 'failed',
        startedAt: '2026-09-10T11:00:00.000Z',
      },
    ]
    const wrapper = mountPanel({ history })
    await openHistory(wrapper)
    const items = wrapper.findAll('[data-testid="history-item"]')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('OK')
    expect(items[0].text()).toContain('{"a":1}')
    expect(items[1].text()).toContain('ERR')
    expect(wrapper.find('[data-testid="result-status"]').text()).toBe('History — 2 run(s)')
  })

  it('truncates long payloads in the history list', async () => {
    const longPayload = { data: 'x'.repeat(200) }
    const wrapper = mountPanel({
      history: [
        {
          id: '1',
          name: 'demo',
          payload: longPayload,
          status: 'success',
          startedAt: '2026-09-10T10:00:00.000Z',
        },
      ],
    })
    await openHistory(wrapper)
    const summary = wrapper.find('[data-testid="history-item"]').text()
    expect(summary).toContain('…')
    expect(summary).not.toContain('x'.repeat(150))
  })

  it('shows the stored result when a history entry is opened', async () => {
    const wrapper = mountPanel({
      history: [
        {
          id: '1',
          name: 'demo',
          payload: { a: 1 },
          output: { result: { stored: true } },
          status: 'success',
          startedAt: '2026-09-10T10:00:00.000Z',
        },
      ],
    })
    await openHistory(wrapper)
    await wrapper.find('[data-testid="history-view"]').trigger('click')

    expect(wrapper.find('[data-testid="history-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="history-detail-payload"]').text()).toContain('"a": 1')
    expect(wrapper.find('[data-testid="runner-result-tab"]').text()).toContain('{"stored":true}')
    expect(wrapper.find('[data-testid="result-status"]').text()).toBe('History — stored result')

    await wrapper.find('[data-testid="history-back"]').trigger('click')
    expect(wrapper.find('[data-testid="history-list"]').exists()).toBe(true)
  })

  it('shows stored errors for failed entries', async () => {
    const wrapper = mountPanel({
      history: [
        {
          id: '1',
          name: 'demo',
          payload: { a: 1 },
          output: { errors: [{ message: 'stored failure' }] },
          status: 'failed',
          startedAt: '2026-09-10T10:00:00.000Z',
        },
      ],
    })
    await openHistory(wrapper)
    await wrapper.find('[data-testid="history-view"]').trigger('click')

    expect(wrapper.find('[data-testid="history-detail"]').text()).toContain('stored failure')
  })

  it('emits rerun with the entry payload', async () => {
    const wrapper = mountPanel({
      history: [
        {
          id: '1',
          name: 'demo',
          payload: { a: 1 },
          output: { result: { ok: true } },
          status: 'success',
          startedAt: '2026-09-10T10:00:00.000Z',
        },
      ],
    })
    await openHistory(wrapper)
    await wrapper.find('[data-testid="history-rerun"]').trigger('click')

    const emitted = wrapper.emitted('rerun')
    expect(emitted).toBeTruthy()
    expect(emitted?.[0]?.[0]).toEqual({ a: 1 })
  })

  it('emits rerun from the detail view', async () => {
    const wrapper = mountPanel({
      history: [
        {
          id: '1',
          name: 'demo',
          payload: { a: 1 },
          output: { result: { ok: true } },
          status: 'success',
          startedAt: '2026-09-10T10:00:00.000Z',
        },
      ],
    })
    await openHistory(wrapper)
    await wrapper.find('[data-testid="history-view"]').trigger('click')
    await wrapper.find('[data-testid="history-run-again"]').trigger('click')

    const emitted = wrapper.emitted('rerun')
    expect(emitted).toBeTruthy()
    expect(emitted?.[0]?.[0]).toEqual({ a: 1 })
  })

  it('disables rerun while a run is in flight', async () => {
    const wrapper = mountPanel({
      status: 'loading',
      history: [
        {
          id: '1',
          name: 'demo',
          payload: { a: 1 },
          status: 'success',
          startedAt: '2026-09-10T10:00:00.000Z',
        },
      ],
    })
    await openHistory(wrapper)
    expect(wrapper.find('[data-testid="history-rerun"]').attributes('disabled')).toBeDefined()
  })

  it('emits clearHistory from the footer', async () => {
    const wrapper = mountPanel({
      history: [
        {
          id: '1',
          name: 'demo',
          payload: { a: 1 },
          status: 'success',
          startedAt: '2026-09-10T10:00:00.000Z',
        },
      ],
    })
    await openHistory(wrapper)
    await wrapper.find('[data-testid="clear-history"]').trigger('click')
    expect(wrapper.emitted('clearHistory')).toBeTruthy()
  })

  it('disables clear when there are no entries', async () => {
    const wrapper = mountPanel({ history: [] })
    await openHistory(wrapper)
    expect(wrapper.find('[data-testid="clear-history"]').attributes('disabled')).toBeDefined()
  })

  it('drops the open entry detail when the entry disappears', async () => {
    const wrapper = mountPanel({
      history: [
        {
          id: '1',
          name: 'demo',
          payload: { a: 1 },
          status: 'success',
          startedAt: '2026-09-10T10:00:00.000Z',
        },
      ],
    })
    await openHistory(wrapper)
    await wrapper.find('[data-testid="history-view"]').trigger('click')
    expect(wrapper.find('[data-testid="history-detail"]').exists()).toBe(true)

    await wrapper.setProps({ history: [] })
    await flushPromises()

    expect(wrapper.find('[data-testid="history-detail"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="history-empty"]').exists()).toBe(true)
  })

  it('renders output type name from schema endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ...schemaResponse,
      outputType: 'BatchOut',
    })

    const wrapper = mountPanel({ type: 'Process' }, fetchMock)
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

    const wrapper = mountPanel({ type: 'Process' }, fetchMock)
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="schema-form"]').exists()).toBe(true)
    const field = wrapper.find('[data-testid="schema-field-result"]')
    expect(field.exists()).toBe(true)
    expect(field.attributes('disabled')).toBeDefined()
  })

  it('shows skeleton while schema is loading in Form mode', async () => {
    const wrapper = mountPanel({ type: 'Process' }, () => new Promise(() => {}))
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="schema-skeleton"]').exists()).toBe(true)
  })

  it('shows error state when schema fetch fails in Form mode', async () => {
    const fetchMock = vi.fn().mockRejectedValue({ statusMessage: 'Server error' })

    const wrapper = mountPanel({ type: 'Process' }, fetchMock)
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

    const wrapper = mountPanel({ type: 'Process' }, fetchMock)
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Output schema unavailable')
  })

  it('resets to JSON mode when name changes', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)

    const wrapper = mountPanel({ type: 'Process' }, fetchMock)
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

    const wrapper = mountPanel({ type: 'Process' }, fetchMock)
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="format-result"]').attributes('disabled')).toBeDefined()
  })

  it('disables the format button while loading', () => {
    const wrapper = mountPanel({ output: { result: { ok: true } }, status: 'loading' })
    expect(wrapper.find('[data-testid="format-result"]').attributes('disabled')).toBeDefined()
  })

  it('shows the read-only output schema in Schema mode', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)

    const wrapper = mountPanel({ type: 'Process' }, fetchMock)
    await flushPromises()

    await wrapper.find('[data-testid="mode-schema"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="schema-view"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="schema-view"]').text()).toContain('result')
    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(false)
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
  it('renders the result again after switching to Schema mode and back to JSON', async () => {
    const fetchMock = vi.fn().mockResolvedValue(schemaResponse)

    const wrapper = mountPanel(
      {
        type: 'Process',
        output: { result: { ok: true } },
        status: 'success',
      },
      fetchMock,
    )
    await flushPromises()

    expect(wrapper.find('[data-testid="runner-result-tab"]').text()).toContain('{"ok":true}')

    await wrapper.find('[data-testid="mode-schema"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="schema-view"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(false)

    await wrapper.find('[data-testid="mode-json"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="runner-result-tab"]').text()).toContain('{"ok":true}')
  })

  it('renders actual output values in Form mode', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      inputSchema: schemaResponse.inputSchema,
      outputSchema: schemaResponse.outputSchema,
    })

    const wrapper = mountPanel(
      {
        type: 'Process',
        output: { result: { result: 'live value' } },
        status: 'success',
      },
      fetchMock,
    )
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    const field = wrapper.find('[data-testid="schema-field-result"]')
    expect(field.exists()).toBe(true)
    expect((field.element as HTMLInputElement).value).toBe('live value')
  })
})
