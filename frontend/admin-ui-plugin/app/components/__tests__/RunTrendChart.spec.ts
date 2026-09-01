import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import RunTrendChart from '../RunTrendChart.vue'

function makeData(overrides = {}) {
  return {
    windowStart: '2026-08-13T10:00:00Z',
    windowEnd: '2026-08-13T13:00:00Z',
    bucketMinutes: 60,
    buckets: [
      {
        bucketStart: '2026-08-13T10:00:00Z',
        statusCounts: { Running: 1, Completed: 2, Failed: 0 },
      },
      {
        bucketStart: '2026-08-13T11:00:00Z',
        statusCounts: { Running: 0, Completed: 1, Failed: 1 },
      },
      {
        bucketStart: '2026-08-13T12:00:00Z',
        statusCounts: { Running: 0, Completed: 0, Failed: 0 },
      },
    ],
    ...overrides,
  }
}

describe('RunTrendChart', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('renders buckets in order and exposes expected test ids', () => {
    const wrapper = mount(RunTrendChart, { props: { data: makeData() } })

    expect(wrapper.find('[data-testid="run-trend-chart"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="run-trend-chart-bars"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="run-trend-chart-x-axis"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="run-trend-chart-y-axis"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="run-trend-chart-legend"]').exists()).toBe(true)

    const bars = wrapper.findAll('[data-testid="run-trend-chart-bars"] rect[aria-label]')
    // 3 buckets * 3 visible statuses (Running, Completed, Failed) = 9 segments.
    expect(bars.length).toBe(9)
    wrapper.unmount()
  })

  it('shows a color swatch for every status in the color map that has data', () => {
    const wrapper = mount(RunTrendChart, {
      props: {
        data: makeData({
          buckets: [
            { bucketStart: '2026-08-13T10:00:00Z', statusCounts: { Completed: 1 } },
            { bucketStart: '2026-08-13T11:00:00Z', statusCounts: { Failed: 1 } },
            { bucketStart: '2026-08-13T12:00:00Z', statusCounts: { Running: 1 } },
          ],
        }),
      },
    })

    const legend = wrapper.find('[data-testid="run-trend-chart-legend"]')
    expect(legend.text()).toContain('Completed')
    expect(legend.text()).toContain('Failed')
    expect(legend.text()).toContain('Running')
    wrapper.unmount()
  })

  it('renders the empty state when no buckets have counts', () => {
    const wrapper = mount(RunTrendChart, {
      props: {
        data: makeData({
          buckets: [
            { bucketStart: '2026-08-13T10:00:00Z', statusCounts: {} },
            { bucketStart: '2026-08-13T11:00:00Z', statusCounts: {} },
          ],
        }),
      },
    })

    expect(wrapper.find('[data-testid="run-trend-chart-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="run-trend-chart-bars"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renders the loading skeleton while loading', () => {
    const wrapper = mount(RunTrendChart, { props: { data: null, loading: true } })

    expect(wrapper.find('[data-testid="run-trend-chart-loading"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('renders the error state and emits retry on click', async () => {
    const wrapper = mount(RunTrendChart, {
      props: { data: null, error: 'Trend unavailable' },
    })

    expect(wrapper.find('[data-testid="run-trend-chart-error"]').text()).toBe(
      'Trend unavailable',
    )

    await wrapper.find('[data-testid="run-trend-chart-retry"]').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
    wrapper.unmount()
  })
})
