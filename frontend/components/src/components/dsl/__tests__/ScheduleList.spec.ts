import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { ScheduleSummary } from '../../types/dsl'
import ScheduleList from '../ScheduleList.vue'

const schedules: ScheduleSummary[] = [
  {
    scheduleId: 'sched-LoanDisbursement',
    definition: 'LoanDisbursement',
    cron: '0 9 * * *',
    timezone: 'UTC',
    note: 'Daily morning run',
    nextRunAt: '2026-09-01T09:00:00Z',
    paused: false,
  },
  {
    scheduleId: 'sched-Repayment',
    definition: 'Repayment',
    cron: '0 */6 * * *',
    timezone: 'Asia/Almaty',
    note: null,
    nextRunAt: null,
    paused: true,
  },
]

function mountList(props: Record<string, unknown>) {
  return mount(ScheduleList, { props })
}

describe('ScheduleList', () => {
  let wrapper: ReturnType<typeof mountList>

  afterEach(() => {
    wrapper?.unmount()
  })

  it('exposes the root data-testid', () => {
    wrapper = mountList({ schedules: [] })

    expect(wrapper.find('[data-testid="schedule-list"]').exists()).toBe(true)
  })

  it('renders schedule rows with definition, cron, timezone and next run', () => {
    wrapper = mountList({ schedules, loading: false })

    const rows = wrapper.findAll('[data-testid="schedule-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('LoanDisbursement')
    expect(rows[0].text()).toContain('0 9 * * *')
    expect(rows[0].text()).toContain('UTC')
    expect(rows[0].text()).toContain('Daily morning run')
    expect(rows[0].text()).toContain('2026-09-01T09:00:00Z')
  })

  it('shows the paused badge only when paused', () => {
    wrapper = mountList({ schedules, loading: false })

    const rows = wrapper.findAll('[data-testid="schedule-row"]')
    expect(rows[0].text()).not.toContain('paused')
    expect(rows[1].text()).toContain('paused')
  })

  it('emits delete with the definition when the delete button is clicked', async () => {
    wrapper = mountList({ schedules, loading: false })

    const buttons = wrapper.findAll('[data-testid="schedule-delete"]')
    await buttons[0].trigger('click')

    expect(wrapper.emitted('delete')).toHaveLength(1)
    expect(wrapper.emitted('delete')![0]).toEqual(['LoanDisbursement'])
  })

  it('emits create with the assembled payload when the form is submitted', async () => {
    wrapper = mountList({ schedules: [], loading: false })

    await wrapper.find('[data-testid="schedule-definition-input"]').setValue('LoanDisbursement')
    await wrapper.find('[data-testid="schedule-cron-input"]').setValue('0 9 * * *')
    await wrapper.find('[data-testid="schedule-timezone-input"]').setValue('Asia/Almaty')
    await wrapper.find('[data-testid="schedule-note-input"]').setValue('morning')
    await wrapper.find('[data-testid="schedule-input-textarea"]').setValue('{"amount":100}')
    await wrapper.find('[data-testid="schedule-create-form"]').trigger('submit')

    expect(wrapper.emitted('create')).toHaveLength(1)
    expect(wrapper.emitted('create')![0]).toEqual([
      {
        definition: 'LoanDisbursement',
        cron: '0 9 * * *',
        timezone: 'Asia/Almaty',
        note: 'morning',
        input: { amount: 100 },
      },
    ])
  })

  it('disables submit when definition or cron is blank', () => {
    wrapper = mountList({ schedules: [], loading: false })

    const submit = wrapper.find('[data-testid="schedule-create-submit"]')
    expect((submit.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('shows an input parse error but does not emit create for invalid JSON', async () => {
    wrapper = mountList({ schedules: [], loading: false })

    await wrapper.find('[data-testid="schedule-definition-input"]').setValue('LoanDisbursement')
    await wrapper.find('[data-testid="schedule-cron-input"]').setValue('0 9 * * *')
    await wrapper.find('[data-testid="schedule-input-textarea"]').setValue('not json')
    await wrapper.find('[data-testid="schedule-create-form"]').trigger('submit')

    expect(wrapper.find('[data-testid="schedule-input-error"]').exists()).toBe(true)
    expect(wrapper.emitted('create')).toBeUndefined()
  })

  it('renders loading skeletons while loading', () => {
    wrapper = mountList({ schedules: [], loading: true })

    expect(wrapper.find('[data-testid="schedule-list-loading"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="schedule-row"]')).toHaveLength(0)
  })

  it('renders the error message when one is provided', () => {
    wrapper = mountList({ schedules: [], error: 'Failed to load schedules' })

    const error = wrapper.find('[data-testid="schedule-list-error"]')
    expect(error.exists()).toBe(true)
    expect(error.text()).toContain('Failed to load schedules')
  })

  it('renders the empty state when no schedules exist', () => {
    wrapper = mountList({ schedules: [] })

    expect(wrapper.find('[data-testid="schedule-list-empty"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No schedules configured.')
  })
})
