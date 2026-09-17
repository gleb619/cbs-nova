import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { DiagnosticsPage, PersistedCompileDiagnostic } from '../../types/dsl'
import ProblemsPanel from '../dsl/ProblemsPanel.vue'

function makeItem(overrides: Partial<PersistedCompileDiagnostic> = {}): PersistedCompileDiagnostic {
  return {
    id: 1,
    occurredAt: '2026-09-13T10:00:00.000Z',
    source: 'PUBLISH',
    definition: 'OrderProcess',
    file: 'OrderProcess.json',
    line: 3,
    column: 7,
    severity: 'error',
    code: 'BLANK_PROCESS_NAME',
    message: 'Process name must not be blank',
    ...overrides,
  }
}

function makePage(overrides: Partial<DiagnosticsPage> = {}): DiagnosticsPage {
  return {
    items: [makeItem()],
    total: 1,
    offset: 0,
    limit: 100,
    ...overrides,
  }
}

function mountPanel(
  fetchPage = vi.fn().mockResolvedValue(makePage()),
  definition = 'OrderProcess',
) {
  return mount(ProblemsPanel, { props: { fetchPage, definition } })
}

describe('ProblemsPanel', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('exposes root data-testid', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.find('[data-testid="problems-panel"]').exists()).toBe(true)
  })

  it('loads diagnostics for the definition and renders groups by code', async () => {
    const fetchPage = vi.fn().mockResolvedValue(
      makePage({
        items: [
          makeItem({ id: 1, code: 'BLANK_PROCESS_NAME' }),
          makeItem({ id: 2, code: 'BLANK_PROCESS_NAME', line: 5 }),
          makeItem({ id: 3, code: 'UNKNOWN_HELPER', message: 'Missing helper' }),
        ],
        total: 3,
      }),
    )
    const wrapper = mountPanel(fetchPage)
    await flushPromises()

    expect(fetchPage).toHaveBeenCalledWith({ definition: 'OrderProcess', limit: 100, offset: 0 })

    const groups = wrapper.findAll('[data-testid="problems-group"]')
    expect(groups).toHaveLength(2)

    expect(groups[0].text()).toContain('BLANK_PROCESS_NAME')
    expect(groups[0].find('[data-testid="problems-group-title"]').text()).toBe('Blank process name')
    expect(groups[0].find('[data-testid="problems-group-count"]').text()).toBe('2')
    expect(groups[0].find('[data-testid="problems-group-severity"]').text()).toBe('error')

    expect(groups[1].text()).toContain('UNKNOWN_HELPER')
    expect(groups[1].find('[data-testid="problems-group-title"]').text()).toBe('Unknown helper')
    expect(groups[1].find('[data-testid="problems-group-severity"]').text()).toBe('error')
  })

  it('renders an unknown code generically without crashing', async () => {
    const wrapper = mountPanel(
      vi.fn().mockResolvedValue(
        makePage({
          items: [makeItem({ id: 1, code: 'WEIRD_UNKNOWN_CODE', severity: 'warning' })],
        }),
      ),
    )
    await flushPromises()

    const group = wrapper.find('[data-testid="problems-group"]')
    expect(group.find('[data-testid="problems-group-title"]').text()).toBe('WEIRD_UNKNOWN_CODE')
    expect(group.find('[data-testid="problems-group-severity"]').text()).toBe('warning')
  })

  it('renders rows with file:line:col and message', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    const row = wrapper.find('[data-testid="problems-row"]')
    expect(row.find('[data-testid="problems-row-location"]').text()).toBe('OrderProcess.json:3:7')
    expect(row.find('[data-testid="problems-row-message"]').text()).toBe(
      'Process name must not be blank',
    )
  })

  it('does not make a row clickable when coordinates are missing', async () => {
    const wrapper = mountPanel(
      vi.fn().mockResolvedValue(
        makePage({
          items: [makeItem({ id: 1, file: null, line: null, column: null })],
        }),
      ),
    )
    await flushPromises()

    expect(wrapper.find('[data-testid="problems-row-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="problems-row-message"]').text()).toBe(
      'Process name must not be blank',
    )
  })

  it('navigates to line and column when a clickable row is selected', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.find('[data-testid="problems-row-button"]').trigger('click')

    const events = wrapper.emitted('navigate') ?? []
    expect(events).toHaveLength(1)
    expect(events[0]).toEqual([{ line: 3, column: 7 }])
  })

  it('emits the total count after loading', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.emitted('count')).toEqual([[1]])
  })

  it('shows the empty state when no diagnostics exist', async () => {
    const wrapper = mountPanel(vi.fn().mockResolvedValue(makePage({ items: [], total: 0 })))
    await flushPromises()

    expect(wrapper.find('[data-testid="problems-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="problems-group"]').exists()).toBe(false)
    expect(wrapper.emitted('count')).toEqual([[0]])
  })

  it('shows the error state with a retry that reloads', async () => {
    const fetchPage = vi.fn().mockRejectedValue(new Error('diagnostics down'))
    const wrapper = mountPanel(fetchPage)
    await flushPromises()

    const errorState = wrapper.find('[data-testid="problems-error"]')
    expect(errorState.exists()).toBe(true)
    expect(errorState.text()).toContain('diagnostics down')

    fetchPage.mockResolvedValueOnce(makePage())
    await errorState.find('[data-testid="error-banner"]').find('button').trigger('click')
    await flushPromises()

    expect(fetchPage).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="problems-row"]').exists()).toBe(true)
  })

  it('reloads when the definition prop changes', async () => {
    const fetchPage = vi.fn().mockResolvedValue(makePage())
    const wrapper = mountPanel(fetchPage)
    await flushPromises()

    await wrapper.setProps({ definition: 'RefundFlow' })
    await flushPromises()

    expect(fetchPage).toHaveBeenLastCalledWith({ definition: 'RefundFlow', limit: 100, offset: 0 })
  })
})
