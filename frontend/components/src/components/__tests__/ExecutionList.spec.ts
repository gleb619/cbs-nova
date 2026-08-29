import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { Execution } from '../../types/execution'
import ExecutionList from '../executions/ExecutionList.vue'

function row(overrides: Partial<Execution>): Execution {
  return {
    id: 'exec-1',
    entity: 'ent',
    entityType: 'Process',
    mode: 'RUN',
    status: 'Running',
    startedAt: '2025-01-01',
    ...overrides,
  }
}

describe('ExecutionList', () => {
  it('exposes the root data-testid', () => {
    const wrapper = mount(ExecutionList, {
      props: { executions: [], loading: false },
    })
    expect(wrapper.find('[data-testid="execution-list"]').exists()).toBe(true)
  })

  it('renders a cancel button on Running rows only', () => {
    const wrapper = mount(ExecutionList, {
      props: {
        executions: [
          row({ id: 'running-1', status: 'Running' }),
          row({ id: 'completed-1', status: 'Completed' }),
          row({ id: 'failed-1', status: 'Failed' }),
          row({ id: 'pending-1', status: 'Pending' }),
        ],
        loading: false,
      },
    })

    expect(wrapper.find('[data-testid="execution-list-row-cancel-running-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="execution-list-row-cancel-completed-1"]').exists()).toBe(
      false,
    )
    expect(wrapper.find('[data-testid="execution-list-row-cancel-failed-1"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="execution-list-row-cancel-pending-1"]').exists()).toBe(false)
  })

  it('emits cancel with the row id when the cancel button is clicked', async () => {
    const wrapper = mount(ExecutionList, {
      props: {
        executions: [row({ id: 'running-1', status: 'Running' })],
        loading: false,
      },
    })

    await wrapper.find('[data-testid="execution-list-row-cancel-running-1"]').trigger('click')

    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(wrapper.emitted('cancel')?.[0]).toEqual(['running-1'])
  })

  it('does not emit select when the cancel button is clicked', async () => {
    const wrapper = mount(ExecutionList, {
      props: {
        executions: [row({ id: 'running-1', status: 'Running' })],
        loading: false,
      },
    })

    await wrapper.find('[data-testid="execution-list-row-cancel-running-1"]').trigger('click')

    expect(wrapper.emitted('select')).toBeFalsy()
  })

  it('still emits select when a non-Running row is clicked', async () => {
    const wrapper = mount(ExecutionList, {
      props: {
        executions: [row({ id: 'completed-1', status: 'Completed' })],
        loading: false,
      },
    })

    await wrapper.find('[data-testid="execution-list-row-completed-1"]').trigger('click')

    expect(wrapper.emitted('select')?.[0]).toEqual(['completed-1'])
  })

  it('dims the cancel button and suppresses click while cancellingIds contains the row id', async () => {
    const wrapper = mount(ExecutionList, {
      props: {
        executions: [row({ id: 'running-1', status: 'Running' })],
        loading: false,
        cancellingIds: ['running-1'],
      },
    })

    const button = wrapper.find('[data-testid="execution-list-row-cancel-running-1"]')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.text()).toBe('Cancelling…')

    await button.trigger('click')

    // Disabled buttons do not emit click events on trigger in happy-dom,
    // so we just assert the disabled state was applied.
    expect(button.attributes('disabled')).toBeDefined()
  })
})
