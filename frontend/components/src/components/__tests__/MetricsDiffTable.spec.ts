import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { MetricsDiffRow } from '../../composables/usePreviewDiff'
import MetricsDiffTable from '../runner/MetricsDiffTable.vue'

function row(overrides: Partial<MetricsDiffRow> = {}): MetricsDiffRow {
  return {
    key: 'k',
    label: 'Metric',
    baseline: 10,
    current: 12,
    delta: 2,
    percentChange: 20,
    lowerIsBetter: true,
    ...overrides,
  }
}

describe('MetricsDiffTable', () => {
  it('renders the root data-testid and a placeholder when rows are empty', () => {
    const wrapper = mount(MetricsDiffTable, { props: { rows: [] } })

    expect(wrapper.find('[data-testid="metrics-diff-table"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No metrics available to compare.')
  })

  it('renders each row with label, baseline and current values', () => {
    const wrapper = mount(MetricsDiffTable, {
      props: {
        rows: [
          row({ key: 'a', label: 'Alpha', baseline: 100, current: 200 }),
          row({ key: 'b', label: 'Beta', baseline: 50, current: 25 }),
        ],
      },
    })

    expect(wrapper.text()).toContain('Alpha')
    expect(wrapper.text()).toContain('Beta')
    expect(wrapper.text()).toContain('100')
    expect(wrapper.text()).toContain('200')
  })

  it('formats execution duration and memory rows with units', () => {
    const wrapper = mount(MetricsDiffTable, {
      props: {
        rows: [
          {
            key: 'executionDurationMs',
            label: 'Duration',
            baseline: 1000,
            current: 1500,
            delta: 500,
            percentChange: 50,
            lowerIsBetter: true,
          },
          {
            key: 'memoryUsedBytes',
            label: 'Memory',
            baseline: 2048,
            current: 1024,
            delta: -1024,
            percentChange: -50,
            lowerIsBetter: true,
          },
        ],
      },
    })

    expect(wrapper.text()).toContain('1,500 ms')
    expect(wrapper.text()).toContain('2.0 KB')
    expect(wrapper.text()).toContain('1.0 KB')
    expect(wrapper.text()).toContain('+500 ms')
    expect(wrapper.text()).toContain('−1.0 KB')
  })

  it('shows an em dash for null baseline or current values', () => {
    const wrapper = mount(MetricsDiffTable, {
      props: {
        rows: [row({ key: 'nulls', label: 'Nulls', baseline: null, current: null, delta: null, percentChange: null })],
      },
    })

    const cells = wrapper.find('tbody tr').findAll('td')
    expect(cells[1].text()).toBe('—')
    expect(cells[2].text()).toBe('—')
    expect(cells[3].text()).toContain('—')
    expect(cells[4].text()).toBe('—')
  })

  it('renders positive, negative and zero deltas with the correct sign', () => {
    const wrapper = mount(MetricsDiffTable, {
      props: {
        rows: [
          row({ key: 'pos', label: 'Pos', delta: 5, baseline: 10, current: 15, percentChange: 50 }),
          row({ key: 'neg', label: 'Neg', delta: -3, baseline: 10, current: 7, percentChange: -30 }),
          row({ key: 'zero', label: 'Zero', delta: 0, baseline: 10, current: 10, percentChange: 0 }),
        ],
      },
    })

    const rows = wrapper.findAll('tbody tr')
    expect(rows[0].text()).toContain('+5')
    expect(rows[1].text()).toContain('−3')
    expect(rows[2].text()).toContain('0')
  })

  it('colours deltas as green when lower is better and the value decreased', () => {
    const wrapper = mount(MetricsDiffTable, {
      props: {
        rows: [
          row({ key: 'improved', label: 'Improved', delta: -5, lowerIsBetter: true }),
          row({ key: 'regressed', label: 'Regressed', delta: 5, lowerIsBetter: true }),
          row({ key: 'worseLower', label: 'Worse', delta: -5, lowerIsBetter: false }),
          row({ key: 'betterHigher', label: 'Better', delta: 5, lowerIsBetter: false }),
        ],
      },
    })

    const rows = wrapper.findAll('tbody tr')
    expect(rows[0].findAll('td')[3].classes()).toContain('text-green-700')
    expect(rows[1].findAll('td')[3].classes()).toContain('text-red-700')
    expect(rows[2].findAll('td')[3].classes()).toContain('text-red-700')
    expect(rows[3].findAll('td')[3].classes()).toContain('text-green-700')
  })

  it('uses gray when the delta is null or zero', () => {
    const wrapper = mount(MetricsDiffTable, {
      props: {
        rows: [
          row({ key: 'nullDelta', label: 'Null', delta: null, percentChange: null }),
          row({ key: 'zeroDelta', label: 'Zero', delta: 0 }),
        ],
      },
    })

    const rows = wrapper.findAll('tbody tr')
    expect(rows[0].findAll('td')[3].classes()).toContain('text-gray-500')
    expect(rows[1].findAll('td')[3].classes()).toContain('text-gray-500')
  })

  it('renders arrows pointing in the correct semantic direction', () => {
    const wrapper = mount(MetricsDiffTable, {
      props: {
        rows: [
          row({ key: 'downGood', label: 'Down', delta: -5, lowerIsBetter: true }),
          row({ key: 'upBad', label: 'Up', delta: 5, lowerIsBetter: true }),
          row({ key: 'neutral', label: 'Neutral', delta: 0 }),
        ],
      },
    })

    const rows = wrapper.findAll('tbody tr')
    expect(rows[0].text()).toContain('↑')
    expect(rows[0].findAll('td')[3].find('span').classes()).toContain('text-green-600')
    expect(rows[1].text()).toContain('↓')
    expect(rows[1].findAll('td')[3].find('span').classes()).toContain('text-red-600')
    expect(rows[2].text()).toContain('·')
    expect(rows[2].findAll('td')[3].find('span').classes()).toContain('text-gray-400')
  })

  it('formats percent changes with a sign and an em dash when missing', () => {
    const wrapper = mount(MetricsDiffTable, {
      props: {
        rows: [
          row({ key: 'pos', label: 'Pos', percentChange: 12.5 }),
          row({ key: 'neg', label: 'Neg', percentChange: -7.5 }),
          row({ key: 'zero', label: 'Zero', percentChange: 0 }),
          row({ key: 'missing', label: 'Missing', percentChange: null }),
        ],
      },
    })

    const rows = wrapper.findAll('tbody tr')
    expect(rows[0].text()).toContain('+12.5%')
    expect(rows[1].text()).toContain('−7.5%')
    expect(rows[2].text()).toContain('0%')
    expect(rows[3].text()).toContain('—')
  })
  it('formats memory thresholds from bytes up to gigabytes', () => {
    const wrapper = mount(MetricsDiffTable, {
      props: {
        rows: [
          {
            key: 'memoryUsedBytes',
            label: 'Tiny',
            baseline: 512,
            current: 1536,
            delta: 1024,
            percentChange: 200,
            lowerIsBetter: true,
          },
          {
            key: 'memoryUsedBytes',
            label: 'Megs',
            baseline: 10485760,
            current: 15728640,
            delta: 5242880,
            percentChange: 50,
            lowerIsBetter: true,
          },
          {
            key: 'memoryUsedBytes',
            label: 'Gigs',
            baseline: 2147483648,
            current: 1073741824,
            delta: -1073741824,
            percentChange: -50,
            lowerIsBetter: true,
          },
        ],
      },
    })

    expect(wrapper.text()).toContain('512 B')
    expect(wrapper.text()).toContain('1.5 KB')
    expect(wrapper.text()).toContain('+1.0 KB')
    expect(wrapper.text()).toContain('10.00 MB')
    expect(wrapper.text()).toContain('15.00 MB')
    expect(wrapper.text()).toContain('+5.00 MB')
    expect(wrapper.text()).toContain('2.00 GB')
    expect(wrapper.text()).toContain('1.00 GB')
    expect(wrapper.text()).toContain('−1.00 GB')
  })

})
