import { mount } from '@vue/test-utils'
import { afterEach, beforeAll, describe, expect, it } from 'vitest'
import type { TransactionExecutionDto, TransactionExecutionStatus } from '../../../types/execution'
import ExecutionTimeline from '../ExecutionTimeline.vue'

const baseDate = new Date('2026-01-01T00:00:00.000Z')

function iso(offsetMs: number): string {
  return new Date(baseDate.getTime() + offsetMs).toISOString()
}

function tx(
  overrides: Partial<TransactionExecutionDto> & { name?: string } = {},
): TransactionExecutionDto {
  const name = overrides.name ?? overrides.transactionName ?? 'apply'
  const executedAt = overrides.executedAt ?? iso(0)
  const { name: _name, transactionName: _txn, executedAt: _executedAt, ...rest } = overrides
  return {
    input: undefined,
    executedAt,
    status: 'SUCCESS' as TransactionExecutionStatus,
    startedAt: executedAt,
    ...rest,
    transactionName: name,
  }
}

const originalGetBoundingClientRect = HTMLElement.prototype.getBoundingClientRect

beforeAll(() => {
  globalThis.ResizeObserver = class {
    observe() {}
    disconnect() {}
    unobserve() {}
  } as unknown as typeof ResizeObserver
})

afterEach(() => {
  HTMLElement.prototype.getBoundingClientRect = originalGetBoundingClientRect
})

function mountTimeline(props: Record<string, unknown>) {
  HTMLElement.prototype.getBoundingClientRect = function (this: HTMLElement) {
    if (this.getAttribute('data-testid') === 'execution-timeline') {
      return { width: 800 } as unknown as DOMRect
    }
    return originalGetBoundingClientRect.call(this)
  }
  return mount(ExecutionTimeline, { props: props as never })
}

describe('ExecutionTimeline', () => {
  it('renders the root data-testid', () => {
    const wrapper = mountTimeline({ transactions: undefined, loading: false, error: null })
    expect(wrapper.find('[data-testid="execution-timeline"]').exists()).toBe(true)
  })

  it('renders a loading placeholder while loading', () => {
    const wrapper = mountTimeline({ transactions: undefined, loading: true, error: null })
    expect(wrapper.find('[data-testid="execution-timeline-loading"]').exists()).toBe(true)
  })

  it('renders an error banner when error is provided', () => {
    const wrapper = mountTimeline({ transactions: undefined, loading: false, error: 'boom' })
    expect(wrapper.find('[data-testid="execution-timeline-error"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('boom')
  })

  it('renders an empty placeholder when transactions are empty', () => {
    const wrapper = mountTimeline({ transactions: [], loading: false, error: null })
    expect(wrapper.find('[data-testid="execution-timeline-empty"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No transactions recorded')
  })

  it('renders mock transactions in chronological order on a single lane', () => {
    const wrapper = mountTimeline({
      transactions: [
        tx({ name: 'second', executedAt: iso(2000), duration: 500 }),
        tx({ name: 'first', executedAt: iso(0), duration: 1000 }),
        tx({ name: 'third', executedAt: iso(1000), duration: 500 }),
      ],
      loading: false,
      error: null,
    })

    const bars = wrapper.findAll('[data-testid^="execution-timeline-bar-"]')
    expect(bars).toHaveLength(3)

    const lefts = bars.map((bar) => {
      return parseFloat((bar.element.parentElement as HTMLElement).style.left)
    })
    expect(lefts[0]).toBeLessThan(lefts[1])
    expect(lefts[1]).toBeLessThan(lefts[2])

    const tops = bars.map((bar) => {
      return parseFloat((bar.element.parentElement as HTMLElement).style.top)
    })
    expect(new Set(tops).size).toBe(1)
  })

  it('switches to swim-lanes when distinct names exceed the threshold', () => {
    const transactions = Array.from({ length: 7 }, (_, i) =>
      tx({ name: `tx-${i}`, executedAt: iso(i * 1000), duration: 500 }),
    )
    const wrapper = mountTimeline({ transactions, loading: false, error: null })

    const tops = wrapper.findAll('[data-testid^="execution-timeline-bar-"]').map((bar) => {
      return parseFloat((bar.element.parentElement as HTMLElement).style.top)
    })
    expect(new Set(tops).size).toBeGreaterThan(1)
  })

  it('places same-name records in the same swim lane', () => {
    const wrapper = mountTimeline({
      transactions: [
        tx({ name: 'shared', executedAt: iso(0), duration: 500 }),
        tx({ name: 'shared', executedAt: iso(3000), duration: 500 }),
      ],
      loading: false,
      error: null,
    })
    const tops = wrapper.findAll('[data-testid^="execution-timeline-bar-"]').map((bar) => {
      return parseFloat((bar.element.parentElement as HTMLElement).style.top)
    })
    expect(new Set(tops).size).toBe(1)
  })

  it('maps every TransactionExecution status to a colour class', () => {
    const statuses: TransactionExecutionStatus[] = ['SUCCESS', 'FAILED', 'COMPENSATED']
    for (const status of statuses) {
      const wrapper = mountTimeline({
        transactions: [tx({ status, duration: 1000 })],
        loading: false,
        error: null,
      })
      const bar = wrapper.find('[data-testid="execution-timeline-bar-0"]')
      const expected =
        status === 'SUCCESS'
          ? 'bg-success-600'
          : status === 'FAILED'
            ? 'bg-error-600'
            : 'bg-warning-500'
      expect(bar.classes()).toContain(expected)
    }
  })

  it('computes bar width proportional to duration over total span times container width', () => {
    const wrapper = mountTimeline({
      transactions: [tx({ name: 'short', duration: 1000 }), tx({ name: 'long', duration: 3000 })],
      loading: false,
      error: null,
    })

    const bars = wrapper.findAll('[data-testid^="execution-timeline-bar-"]')
    const widths = bars.map((bar) => parseFloat((bar.element as HTMLElement).style.width))
    expect(widths[0]).toBeCloseTo(800 / 3, 0)
    expect(widths[1]).toBeCloseTo(800, 0)
  })

  it('shows a tooltip with name, status, duration and error on hover', async () => {
    const wrapper = mountTimeline({
      transactions: [
        tx({
          name: 'fail-tx',
          status: 'FAILED',
          executedAt: iso(0),
          finishedAt: iso(2000),
          error: 'something broke',
        }),
      ],
      loading: false,
      error: null,
    })

    const item = wrapper.find('[data-testid="execution-timeline-item"]')
    await item.trigger('mouseenter')

    const tooltip = wrapper.find('[data-testid="execution-timeline-tooltip"]')
    expect(tooltip.exists()).toBe(true)
    expect(tooltip.text()).toContain('fail-tx')
    expect(tooltip.text()).toContain('FAILED')
    expect(tooltip.text()).toContain('something broke')
  })

  it('emits select when a bar is clicked', async () => {
    const transactions = [tx({ name: 'click-me', duration: 1000 })]
    const wrapper = mountTimeline({ transactions, loading: false, error: null })

    await wrapper.find('[data-testid="execution-timeline-bar-0"]').trigger('click')

    expect(wrapper.emitted('select')).toHaveLength(1)
    expect((wrapper.emitted('select')![0] as TransactionExecutionDto[])[0].transactionName).toBe(
      'click-me',
    )
  })
})
