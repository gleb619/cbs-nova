import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { DiagnosticsPage, PersistedCompileDiagnostic } from '../../types/dsl'
import DiagnosticsHistoryPanel from '../dsl/DiagnosticsHistoryPanel.vue'

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
    code: 'DSL001',
    message: 'Unknown helper "frobnicate"',
    ...overrides,
  }
}

function makePage(overrides: Partial<DiagnosticsPage> = {}): DiagnosticsPage {
  return {
    items: [makeItem(), makeItem({ id: 2, severity: 'warning', definition: 'RefundFlow' })],
    total: 2,
    offset: 0,
    limit: 25,
    ...overrides,
  }
}

function mountPanel(fetchPage = vi.fn().mockResolvedValue(makePage())) {
  const wrapper = mount(DiagnosticsHistoryPanel, { props: { fetchPage } })
  return { wrapper, fetchPage }
}

describe('DiagnosticsHistoryPanel', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('loads the first page on mount and renders its rows', async () => {
    const { wrapper, fetchPage } = mountPanel()
    await flushPromises()

    expect(fetchPage).toHaveBeenCalledWith({ definition: '', limit: 25, offset: 0 })
    const rows = wrapper.findAll('[data-testid="diagnostics-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('OrderProcess')
    expect(rows[1].text()).toContain('RefundFlow')
  })

  it('renders relative time with the ISO timestamp as title and file:line:col with code', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    const firstRow = wrapper.findAll('[data-testid="diagnostics-row"]')[0]
    expect(firstRow.find('td').attributes('title')).toBe('2026-09-13T10:00:00.000Z')
    expect(firstRow.text()).toContain('ago')
    expect(firstRow.text()).toContain('OrderProcess.json:3:7')
    expect(firstRow.text()).toContain('[DSL001]')
  })

  it('styles the severity badge by severity', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    const badges = wrapper.findAll('[data-testid="diagnostics-severity-badge"]')
    expect(badges[0].classes()).toContain('bg-red-100')
    expect(badges[0].text()).toBe('error')
    expect(badges[1].classes()).toContain('bg-yellow-100')
  })

  it('falls back to a neutral badge for unknown severities', async () => {
    const { wrapper } = mountPanel(
      vi.fn().mockResolvedValue(makePage({ items: [makeItem({ severity: 'fatal' })] })),
    )
    await flushPromises()

    const badge = wrapper.find('[data-testid="diagnostics-severity-badge"]')
    expect(badge.classes()).toContain('bg-gray-100')
  })

  it('filters by severity client-side without another server round-trip', async () => {
    const { wrapper, fetchPage } = mountPanel()
    await flushPromises()

    await wrapper.find('[data-testid="diagnostics-filter-severity"]').setValue('error')

    const rows = wrapper.findAll('[data-testid="diagnostics-row"]')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('OrderProcess')
    expect(fetchPage).toHaveBeenCalledTimes(1)
  })

  it('round-trips the definition filter through the server and resets to the first page', async () => {
    const { wrapper, fetchPage } = mountPanel()
    await flushPromises()

    await wrapper.find('[data-testid="diagnostics-filter-definition"]').setValue('Order')
    await wrapper.find('form').trigger('submit')

    expect(fetchPage).toHaveBeenLastCalledWith({ definition: 'Order', limit: 25, offset: 0 })
  })

  it('pages forward and back through the pager', async () => {
    const fetchPage = vi.fn().mockImplementation(async (params: { offset: number }) =>
      makePage({
        items: [makeItem({ id: params.offset + 1 })],
        total: 60,
        offset: params.offset,
        limit: 25,
      }),
    )
    const { wrapper } = mountPanel(fetchPage)
    await flushPromises()

    expect(wrapper.find('[data-testid="diagnostics-range"]').text()).toBe('1–1 of 60')
    expect(
      wrapper.find('[data-testid="diagnostics-pager-prev"]').attributes('disabled'),
    ).toBeDefined()

    await wrapper.find('[data-testid="diagnostics-pager-next"]').trigger('click')
    await flushPromises()
    expect(fetchPage).toHaveBeenLastCalledWith({ definition: '', limit: 25, offset: 25 })
    expect(wrapper.find('[data-testid="diagnostics-range"]').text()).toBe('26–26 of 60')
    expect(
      wrapper.find('[data-testid="diagnostics-pager-prev"]').attributes('disabled'),
    ).toBeUndefined()

    await wrapper.find('[data-testid="diagnostics-pager-prev"]').trigger('click')
    await flushPromises()
    expect(fetchPage).toHaveBeenLastCalledWith({ definition: '', limit: 25, offset: 0 })
    expect(wrapper.find('[data-testid="diagnostics-range"]').text()).toBe('1–1 of 60')
  })

  it('disables next when the last page is shown', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    expect(
      wrapper.find('[data-testid="diagnostics-pager-next"]').attributes('disabled'),
    ).toBeDefined()
  })

  it('shows the empty state when no diagnostics are recorded', async () => {
    const { wrapper } = mountPanel(vi.fn().mockResolvedValue(makePage({ items: [], total: 0 })))
    await flushPromises()

    expect(wrapper.find('[data-testid="diagnostics-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="diagnostics-row"]').exists()).toBe(false)
  })

  it('shows the error state with a retry that reloads', async () => {
    const fetchPage = vi.fn().mockRejectedValue(new Error('backend down'))
    const { wrapper } = mountPanel(fetchPage)
    await flushPromises()

    const errorState = wrapper.find('[data-testid="diagnostics-error"]')
    expect(errorState.exists()).toBe(true)
    expect(errorState.text()).toContain('backend down')

    fetchPage.mockResolvedValueOnce(makePage())
    await errorState.find('[data-testid="error-banner"]').find('button').trigger('click')
    await flushPromises()

    expect(fetchPage).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="diagnostics-row"]').exists()).toBe(true)
  })
})
