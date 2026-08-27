import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DryRunLogsTab from '../runner/DryRunLogsTab.vue'

function mountDryRunLogsTab(props: Record<string, unknown>) {
  return mount(DryRunLogsTab, { props })
}

describe('DryRunLogsTab', () => {
  it('shows an empty placeholder when logs are undefined', () => {
    const wrapper = mountDryRunLogsTab({ logs: undefined })
    expect(wrapper.text()).toContain('No logs captured during dry run.')
  })

  it('shows an empty placeholder when logs are empty', () => {
    const wrapper = mountDryRunLogsTab({ logs: [] })
    expect(wrapper.text()).toContain('No logs captured during dry run.')
  })

  it('renders a single INFO entry with timestamp, badge, logger, and message', () => {
    const wrapper = mountDryRunLogsTab({
      logs: [
        {
          timestamp: '2026-07-19T10:00:00Z',
          level: 'INFO',
          logger: 'com.cbs.Preview',
          message: 'Dry run started',
        },
      ],
    })

    expect(wrapper.text()).toContain('[2026-07-19T10:00:00Z]')
    expect(wrapper.text()).toContain('INFO')
    expect(wrapper.text()).toContain('com.cbs.Preview')
    expect(wrapper.text()).toContain('Dry run started')

    const badge = wrapper.find('span.text-blue-600')
    expect(badge.exists()).toBe(true)
    expect(badge.classes()).toContain('bg-blue-50')
  })

  it('renders an ERROR entry with red badge styling', () => {
    const wrapper = mountDryRunLogsTab({
      logs: [
        {
          timestamp: '2026-07-19T10:01:00Z',
          level: 'ERROR',
          logger: 'com.cbs.Preview',
          message: 'Something failed',
        },
      ],
    })

    const badge = wrapper.find('span.text-red-600')
    expect(badge.exists()).toBe(true)
    expect(badge.classes()).toContain('bg-red-50')
    expect(wrapper.text()).toContain('Something failed')
  })

  it('renders multiple entries', () => {
    const wrapper = mountDryRunLogsTab({
      logs: [
        {
          timestamp: '2026-07-19T10:00:00Z',
          level: 'INFO',
          logger: 'com.cbs.Preview',
          message: 'First',
        },
        {
          timestamp: '2026-07-19T10:00:01Z',
          level: 'WARN',
          logger: 'com.cbs.Preview',
          message: 'Second',
        },
        {
          timestamp: '2026-07-19T10:00:02Z',
          level: 'DEBUG',
          logger: 'com.cbs.Preview',
          message: 'Third',
        },
      ],
    })

    expect(wrapper.text()).toContain('First')
    expect(wrapper.text()).toContain('Second')
    expect(wrapper.text()).toContain('Third')

    const debugBadge = wrapper.find('span[data-level="DEBUG"]')
    expect(debugBadge.exists()).toBe(true)
    expect(debugBadge.classes()).toContain('bg-gray-100')
  })

  it('renders a Copy all button wired to navigator.clipboard.writeText', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })

    const wrapper = mountDryRunLogsTab({
      logs: [
        {
          timestamp: '2026-07-19T10:00:00Z',
          level: 'INFO',
          logger: 'com.cbs.Preview',
          message: 'Hello',
        },
      ],
    })

    const button = wrapper.find('button')
    expect(button.exists()).toBe(true)
    expect(button.text()).toBe('Copy all')

    await button.trigger('click')
    expect(writeText).toHaveBeenCalledWith('[2026-07-19T10:00:00Z] INFO  com.cbs.Preview — Hello')

    vi.unstubAllGlobals()
  })

  it('displays the log count badge', () => {
    const wrapper = mountDryRunLogsTab({
      logs: [
        {
          timestamp: '2026-07-19T10:00:00Z',
          level: 'INFO',
          logger: 'com.cbs.Preview',
          message: 'First',
        },
        {
          timestamp: '2026-07-19T10:00:01Z',
          level: 'INFO',
          logger: 'com.cbs.Preview',
          message: 'Second',
        },
      ],
    })

    expect(wrapper.text()).toContain('2 logs')
  })

  it('stamps data-testid on the root, list, rows, count, and copy-all button', () => {
    const wrapper = mountDryRunLogsTab({
      logs: [
        {
          timestamp: '2026-07-19T10:00:00Z',
          level: 'INFO',
          logger: 'com.cbs.Preview',
          message: 'First',
        },
        {
          timestamp: '2026-07-19T10:00:01Z',
          level: 'WARN',
          logger: 'com.cbs.Preview',
          message: 'Second',
        },
      ],
    })

    expect(wrapper.find('[data-testid="dry-run-logs-tab"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="dry-run-logs-list"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="dry-run-logs-row"]')).toHaveLength(2)
    expect(wrapper.find('[data-testid="dry-run-logs-count"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="dry-run-logs-copy-all"]').exists()).toBe(true)
  })
})
