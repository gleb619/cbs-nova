import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { PreviewHistoryEntry, RunnerStatus } from '../../types/runner'
import PreviewHistoryPanel from '../dsl/PreviewHistoryPanel.vue'
import ResultTab from '../runner/ResultTab.vue'

function entry(overrides: Partial<PreviewHistoryEntry> = {}): PreviewHistoryEntry {
  return {
    id: 'entry-1',
    name: 'Preview #1',
    payload: { amount: 100 },
    status: 'success',
    startedAt: '2026-09-13T10:00:00Z',
    ...overrides,
  }
}

const mountPanel = (
  props: {
    history?: PreviewHistoryEntry[]
    selectedId?: string | null
    status?: RunnerStatus
  } = {},
) =>
  mount(PreviewHistoryPanel, {
    props: { history: [], selectedId: null, status: 'idle', ...props },
    global: { components: { ResultTab } },
  })

const formattedTime = (iso: string): string => new Date(iso).toLocaleString()

describe('PreviewHistoryPanel', () => {
  it('exposes the root data-testid', () => {
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="history-panel"]').exists()).toBe(true)
  })

  it('renders one row per history entry with status, time, and payload summary', () => {
    const entries = [
      entry({ id: 'a', status: 'success', payload: { amount: 100 } }),
      entry({
        id: 'b',
        status: 'failed',
        name: 'Preview #2',
        payload: { amount: 200 },
        startedAt: '2026-09-14T11:00:00Z',
      }),
    ]
    const wrapper = mountPanel({ history: entries })

    const rows = wrapper.findAll('[data-testid="history-item"]')
    expect(rows).toHaveLength(2)

    expect(rows[0].text()).toContain('OK')
    expect(rows[0].text()).toContain(formattedTime('2026-09-13T10:00:00Z'))
    expect(rows[0].text()).toContain('{"amount":100}')

    expect(rows[1].text()).toContain('ERR')
    expect(rows[1].text()).toContain(formattedTime('2026-09-14T11:00:00Z'))
    expect(rows[1].text()).toContain('{"amount":200}')
  })

  it('truncates long payload summaries', () => {
    const longPayload = { text: 'a'.repeat(120) }
    const wrapper = mountPanel({ history: [entry({ payload: longPayload })] })

    const rowText = wrapper.find('[data-testid="history-item"]').text()
    expect(rowText).toContain('a'.repeat(60))
    expect(rowText).toContain('…')
  })

  it('emits update:selectedId when an entry View button is clicked', async () => {
    const wrapper = mountPanel({ history: [entry({ id: 'entry-a' }), entry({ id: 'entry-b' })] })

    await wrapper.findAll('[data-testid="history-view"]')[1].trigger('click')

    expect(wrapper.emitted('update:selectedId')).toEqual([['entry-b']])
  })

  it('emits rerun with the entry payload from the list row', async () => {
    const payload = { amount: 42 }
    const wrapper = mountPanel({ history: [entry({ id: 'entry-a', payload })] })

    await wrapper.find('[data-testid="history-rerun"]').trigger('click')

    expect(wrapper.emitted('rerun')).toEqual([[payload]])
  })

  it('disables rerun buttons while loading or running', () => {
    const wrapper = mountPanel({
      history: [entry()],
      status: 'loading',
    })

    expect(wrapper.find('[data-testid="history-rerun"]').attributes('disabled')).toBeDefined()

    const running = mountPanel({ history: [entry()], status: 'running' })
    expect(running.find('[data-testid="history-rerun"]').attributes('disabled')).toBeDefined()

    const idle = mountPanel({ history: [entry()], status: 'idle' })
    expect(idle.find('[data-testid="history-rerun"]').attributes('disabled')).toBeUndefined()
  })

  it('shows the detail view for the selected entry and the back button', () => {
    const payload = { amount: 100 }
    const wrapper = mountPanel({
      history: [entry({ id: 'entry-a', payload })],
      selectedId: 'entry-a',
    })

    expect(wrapper.find('[data-testid="history-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="history-back"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Success')
    expect(wrapper.text()).toContain(formattedTime('2026-09-13T10:00:00Z'))
    expect(wrapper.find('[data-testid="history-detail-payload"]').text()).toContain('"amount": 100')
  })

  it('renders failed status in the detail view', () => {
    const wrapper = mountPanel({
      history: [entry({ id: 'entry-a', status: 'failed' })],
      selectedId: 'entry-a',
    })

    expect(wrapper.text()).toContain('Failed')
  })

  it('emits update:selectedId with null when Back is clicked', async () => {
    const wrapper = mountPanel({
      history: [entry({ id: 'entry-a' })],
      selectedId: 'entry-a',
    })

    await wrapper.find('[data-testid="history-back"]').trigger('click')

    expect(wrapper.emitted('update:selectedId')).toEqual([[null]])
  })

  it('emits rerun with the selected entry payload from the detail view', async () => {
    const payload = { amount: 99 }
    const wrapper = mountPanel({
      history: [entry({ id: 'entry-a', payload })],
      selectedId: 'entry-a',
    })

    await wrapper.find('[data-testid="history-run-again"]').trigger('click')

    expect(wrapper.emitted('rerun')).toEqual([[payload]])
  })

  it('disables the detail run-again button while loading or running', () => {
    const wrapper = mountPanel({
      history: [entry({ id: 'entry-a' })],
      selectedId: 'entry-a',
      status: 'loading',
    })

    expect(wrapper.find('[data-testid="history-run-again"]').attributes('disabled')).toBeDefined()
  })

  it('renders output errors in the detail view', () => {
    const wrapper = mountPanel({
      history: [
        entry({
          id: 'entry-a',
          output: { errors: [{ message: 'boom', code: 'ERR' }] },
        }),
      ],
      selectedId: 'entry-a',
    })

    expect(wrapper.text()).toContain('boom')
  })

  it('falls back to ResultTab when the selected entry has no errors', () => {
    const wrapper = mountPanel({
      history: [entry({ id: 'entry-a', output: { result: { ok: true } } })],
      selectedId: 'entry-a',
    })

    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="runner-result-tab-output"]').text()).toContain('"ok": true')
  })

  it('ignores a stale selectedId that is no longer in history', () => {
    const wrapper = mountPanel({
      history: [entry({ id: 'entry-a' })],
      selectedId: 'missing',
    })

    expect(wrapper.find('[data-testid="history-list"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="history-detail"]').exists()).toBe(false)
  })

  it('shows the empty state when no history entries are provided', () => {
    const wrapper = mountPanel()

    expect(wrapper.find('[data-testid="history-empty"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No runs yet.')
    expect(wrapper.find('[data-testid="history-item"]').exists()).toBe(false)
  })
})
