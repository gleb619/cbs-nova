import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import LogsTab from '../executions/LogsTab.vue'

function log(overrides: Record<string, unknown> = {}) {
  return {
    timestamp: '2025-06-15T10:00:00Z',
    severity: 'info',
    message: 'hello',
    ...overrides,
  }
}

describe('LogsTab', () => {
  it('renders the root data-testid and an empty placeholder when logs are undefined', () => {
    const wrapper = mount(LogsTab, { props: { logs: undefined } })

    expect(wrapper.find('[data-testid="executions-logs-tab"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No log entries.')
  })

  it('renders an empty placeholder when logs are empty', () => {
    const wrapper = mount(LogsTab, { props: { logs: [] } })

    expect(wrapper.text()).toContain('No log entries.')
  })

  it('renders each log row with timestamp, severity, step and message', () => {
    const wrapper = mount(LogsTab, {
      props: {
        logs: [
          log({
            step: 'StepA',
            severity: 'warn',
            message: 'watch out',
          }),
        ],
      },
    })

    const row = wrapper.find('[data-testid="executions-logs-row-0"]')
    expect(row.exists()).toBe(true)
    expect(row.text()).toContain(new Date('2025-06-15T10:00:00Z').toLocaleString())
    expect(row.text()).toContain('warn')
    expect(row.text()).toContain('StepA')
    expect(row.text()).toContain('watch out')
  })

  it('filters rows by severity', async () => {
    const wrapper = mount(LogsTab, {
      props: {
        logs: [log({ severity: 'info' }), log({ severity: 'error', message: 'bad' })],
      },
    })

    expect(wrapper.findAll('[data-testid^="executions-logs-row-"]')).toHaveLength(2)
    await wrapper.find('select').setValue('error')

    expect(wrapper.findAll('[data-testid^="executions-logs-row-"]')).toHaveLength(1)
    expect(wrapper.find('[data-testid="executions-logs-row-0"]').text()).toContain('bad')
  })

  it('filters rows by message search text', async () => {
    const wrapper = mount(LogsTab, {
      props: {
        logs: [log({ message: 'alpha' }), log({ message: 'beta' })],
      },
    })

    await wrapper.find('input[type="text"]').setValue('beta')

    expect(wrapper.findAll('[data-testid^="executions-logs-row-"]')).toHaveLength(1)
    expect(wrapper.find('[data-testid="executions-logs-row-0"]').text()).toContain('beta')
  })

  it('applies default styling for unknown severity values', () => {
    const wrapper = mount(LogsTab, {
      props: { logs: [log({ severity: 'trace' })] },
    })

    const row = wrapper.find('[data-testid="executions-logs-row-0"]')
    expect(row.text()).toContain('trace')
    const severitySpan = row.findAll('span')[1]
    expect(severitySpan.classes()).toContain('bg-gray-100')
  })

  it('omits the step column when step is not provided', () => {
    const wrapper = mount(LogsTab, {
      props: { logs: [log()] },
    })

    const spans = wrapper.find('[data-testid="executions-logs-row-0"]').findAll('span')
    expect(spans.length).toBe(3)
  })
})
