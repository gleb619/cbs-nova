import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  __resetHierarchyHistoryForTests,
  HIERARCHY_HISTORY_STORAGE_KEY,
  HIERARCHY_HISTORY_STORAGE_NAMESPACE,
} from '../../composables/usePreviewHistory'
import type { RunnerOutput } from '../../types/runner'
import HierarchyTab from '../dsl/HierarchyTab.vue'

function mountTab(
  props: Record<string, unknown> = {},
  hierarchy: (
    name: string,
    body: unknown,
    metadata?: Record<string, unknown>,
  ) => RunnerOutput | Promise<RunnerOutput> = vi
    .fn<
      (name: string, body: unknown, metadata?: Record<string, unknown>) => Promise<RunnerOutput>
    >()
    .mockResolvedValue({ hierarchy: { nodes: [] } } as unknown as RunnerOutput),
) {
  return mount(HierarchyTab, {
    props: { name: 'demo', hierarchy, ...props },
  })
}

describe('HierarchyTab', () => {
  beforeEach(() => {
    __resetHierarchyHistoryForTests()
    window.localStorage.clear()
  })

  afterEach(() => {
    vi.resetModules()
  })

  it('renders options panel and result panel with hierarchy context', () => {
    const wrapper = mountTab()
    expect(wrapper.find('[data-testid="hierarchy-options-panel"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="hierarchy-result-panel"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Hierarchy options')
    expect(wrapper.text()).toContain('Result · hierarchy')
  })

  it('disables Run while a request is in flight', async () => {
    let resolve!: (value: RunnerOutput) => void
    const hierarchy = vi
      .fn<
        (name: string, body: unknown, metadata?: Record<string, unknown>) => Promise<RunnerOutput>
      >()
      .mockImplementation(
        () =>
          new Promise<RunnerOutput>((res) => {
            resolve = res
          }),
      )
    const wrapper = mountTab({}, hierarchy)
    const runButton = wrapper.find('[data-testid="run-button"]')
    expect(runButton.attributes('disabled')).toBeUndefined()

    await runButton.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="run-button"]').attributes('disabled')).toBeDefined()

    resolve({ nodes: [] } as unknown as RunnerOutput)
    await flushPromises()
    expect(wrapper.find('[data-testid="run-button"]').attributes('disabled')).toBeUndefined()
  })

  it('calls hierarchy prop with structured options and workbench metadata', async () => {
    const hierarchy = vi
      .fn<
        (name: string, body: unknown, metadata?: Record<string, unknown>) => Promise<RunnerOutput>
      >()
      .mockResolvedValue({ nodes: [] } as unknown as RunnerOutput)
    const wrapper = mountTab({}, hierarchy)

    await wrapper.find('[data-testid="include-signals"] input').setValue(false)
    await wrapper.find('[data-testid="include-queries"] input').setValue(false)

    const runButton = wrapper.find('[data-testid="run-button"]')
    await runButton.trigger('click')
    await flushPromises()

    expect(hierarchy).toHaveBeenCalledTimes(1)
    const [name, payload, metadata] = hierarchy.mock.calls[0]!
    expect(name).toBe('demo')
    expect(payload).toEqual({
      depth: 4,
      includeActivities: true,
      includeSignals: false,
      includeQueries: false,
    })
    expect(metadata).toEqual({ startedFrom: 'workbench', endpoint: 'hierarchy' })
    expect(wrapper.find('[data-testid="hierarchy-result-status"]').text()).toBe('done')
  })

  it('clicks Run with the typed config and renders the raw response', async () => {
    const hierarchy = vi
      .fn<
        (name: string, body: unknown, metadata?: Record<string, unknown>) => Promise<RunnerOutput>
      >()
      .mockResolvedValue({ result: { children: [{ name: 'root' }] } } as unknown as RunnerOutput)
    const wrapper = mountTab({}, hierarchy)

    await wrapper.find('[data-testid="depth-input"]').setValue('7')
    await wrapper.find('[data-testid="run-button"]').trigger('click')
    await flushPromises()

    const payload = hierarchy.mock.calls[0]![1] as Record<string, unknown>
    expect(payload.depth).toBe(7)
    expect(wrapper.find('[data-testid="hierarchy-result-text"]').text()).toContain('children')
  })

  it('surfaces backend errors and exposes the failure status', async () => {
    const hierarchy = vi.fn().mockRejectedValue({
      data: { errors: [{ message: 'hierarchy failed' }] },
      statusMessage: 'Unprocessable Entity',
    })

    const wrapper = mountTab({}, hierarchy as never)
    await wrapper.find('[data-testid="run-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="hierarchy-result-status"]').text()).toBe('failed')
    const resultText = wrapper.find('[data-testid="hierarchy-result-text"]').text()
    expect(resultText).toContain('hierarchy failed')
  })

  it('falls back to statusMessage when the BFF envelope has no message', async () => {
    const hierarchy = vi.fn().mockRejectedValue({
      data: { code: 'BACKEND_TIMEOUT' },
      statusMessage: 'Hierarchy request timed out',
    })

    const wrapper = mountTab({}, hierarchy as never)
    await wrapper.find('[data-testid="run-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="hierarchy-result-text"]').text()).toContain(
      'Hierarchy request timed out',
    )
  })

  it('records the run in hierarchy history', async () => {
    const hierarchy = vi
      .fn<
        (name: string, body: unknown, metadata?: Record<string, unknown>) => Promise<RunnerOutput>
      >()
      .mockResolvedValue({ result: { name: 'demo' } } as unknown as RunnerOutput)
    const wrapper = mountTab({}, hierarchy)

    await wrapper.find('[data-testid="run-button"]').trigger('click')
    await flushPromises()

    const stored = JSON.parse(
      window.localStorage.getItem(
        `${HIERARCHY_HISTORY_STORAGE_NAMESPACE}:${HIERARCHY_HISTORY_STORAGE_KEY}`,
      ) ?? '[]',
    )
    expect(stored).toHaveLength(1)
    expect(stored[0]).toMatchObject({
      name: 'demo',
      payload: {
        depth: 4,
        includeActivities: true,
        includeSignals: true,
        includeQueries: true,
      },
      status: 'success',
    })
    expect(stored[0].id).toBeTruthy()
    expect(stored[0].startedAt).toBeTruthy()
  })

  it('clears output state when the name prop changes', async () => {
    const wrapper = mountTab()
    await wrapper.setProps({ name: 'other' })
    expect(wrapper.find('[data-testid="hierarchy-result-empty"]').exists()).toBe(true)
  })
})
