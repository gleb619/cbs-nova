import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ExecutionStatus } from '../../types/execution'
import RecentExecutions from '../dashboard/RecentExecutions.vue'

function exec(id: string, status: ExecutionStatus) {
  return { id, entity: `Entity ${id}`, status, startedAt: '2025-06-15T10:00:00Z' }
}

describe('RecentExecutions', () => {
  it('renders the root data-testid and a loading message', () => {
    const wrapper = mount(RecentExecutions, {
      props: { executions: [], loading: true },
    })

    expect(wrapper.find('[data-testid="recent-executions"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Loading...')
  })

  it('renders an empty message when not loading and there are no executions', () => {
    const wrapper = mount(RecentExecutions, {
      props: { executions: [], loading: false },
    })

    expect(wrapper.text()).toContain('No recent executions.')
  })

  it('renders rows and emits select with the row id when clicked', async () => {
    const wrapper = mount(RecentExecutions, {
      props: {
        executions: [exec('1', 'SUCCESS'), exec('2', 'FAILED')],
        loading: false,
      },
    })

    expect(wrapper.findAll('[data-testid^="recent-executions-row-"]')).toHaveLength(2)

    await wrapper.find('[data-testid="recent-executions-row-1"]').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['1']])
  })

  it('applies the correct status colour classes for each status', () => {
    const statuses: ExecutionStatus[] = ['SUCCESS', 'FAILED', 'RUNNING', 'Stale']
    const executions = statuses.map((s, i) => exec(String(i), s))

    const wrapper = mount(RecentExecutions, {
      props: { executions, loading: false },
    })

    const rows = wrapper.findAll('tbody tr')
    expect(rows.length).toBe(statuses.length)
    expect(rows[0].find('span').classes()).toContain('bg-green-100')
    expect(rows[1].find('span').classes()).toContain('bg-red-100')
    expect(rows[2].find('span').classes()).toContain('bg-yellow-100')
    expect(rows[3].find('span').classes()).toContain('bg-amber-100')
  })

  it('falls back to a gray status class for unrecognised statuses', () => {
    const wrapper = mount(RecentExecutions, {
      props: { executions: [exec('1', 'Pending' as ExecutionStatus)], loading: false },
    })

    expect(wrapper.find('tbody tr').find('span').classes()).toContain('bg-gray-100')
  })

  it('formats the startedAt timestamp', () => {
    const wrapper = mount(RecentExecutions, {
      props: { executions: [exec('1', 'SUCCESS')], loading: false },
    })

    expect(wrapper.text()).toContain(new Date('2025-06-15T10:00:00Z').toLocaleString())
  })
})
