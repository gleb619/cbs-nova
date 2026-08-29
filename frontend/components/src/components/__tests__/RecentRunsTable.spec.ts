import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { Execution } from '../../types/execution'
import StatusBadge from '../executions/StatusBadge.vue'
import RecentRunsTable from '../dashboard/RecentRunsTable.vue'

// The table renders status cells through ExecutionsStatusBadge (Nuxt
// auto-resolves it inside the plugin app); register the real component so the
// spec exercises the actual badge rendering.
function mountTable(props: { executions: Execution[]; loading?: boolean }) {
  return mount(RecentRunsTable, {
    props,
    global: {
      components: { ExecutionsStatusBadge: StatusBadge },
    },
  })
}

function execution(overrides: Partial<Execution>): Execution {
  return {
    id: 'run-1',
    entity: 'LoanDisbursement',
    entityType: 'Process',
    mode: 'RUN',
    status: 'Completed',
    startedAt: '2026-08-29T09:00:00Z',
    completedAt: '2026-08-29T09:00:05Z',
    ...overrides,
  }
}

describe('RecentRunsTable', () => {
  it('renders process name, status badge, started and finished per row', () => {
    const wrapper = mountTable({
      executions: [
        execution({ id: 'run-1', status: 'Completed' }),
        execution({ id: 'run-2', entity: 'CreditScoring', status: 'Failed', completedAt: undefined }),
      ],
    })

    expect(wrapper.find('[data-testid="recent-runs-table"]').exists()).toBe(true)

    const row1 = wrapper.find('[data-testid="recent-runs-table-row-run-1"]')
    expect(row1.exists()).toBe(true)
    expect(row1.text()).toContain('LoanDisbursement')
    expect(row1.find('[data-testid="status-badge"]').text()).toBe('Completed')

    const row2 = wrapper.find('[data-testid="recent-runs-table-row-run-2"]')
    expect(row2.exists()).toBe(true)
    expect(row2.text()).toContain('CreditScoring')
    expect(row2.find('[data-testid="status-badge"]').text()).toBe('Failed')
    // Unfinished run: finished column falls back to an em dash.
    expect(row2.text()).toContain('—')
  })

  it('renders skeleton rows while loading and no run rows', () => {
    const wrapper = mountTable({ executions: [], loading: true })

    expect(wrapper.findAll('tbody tr').length).toBe(5)
    expect(wrapper.find('[data-testid^="recent-runs-table-row-"]').exists()).toBe(false)
    expect(wrapper.find('.animate-pulse').exists()).toBe(true)
  })

  it('renders an empty state when there are no runs and not loading', () => {
    const wrapper = mountTable({ executions: [] })

    expect(wrapper.text()).toContain('No recent executions.')
  })

  it('emits select with the run id when a row is clicked', async () => {
    const wrapper = mountTable({ executions: [execution({ id: 'run-42' })] })

    await wrapper.find('[data-testid="recent-runs-table-row-run-42"]').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['run-42']])
  })
})
