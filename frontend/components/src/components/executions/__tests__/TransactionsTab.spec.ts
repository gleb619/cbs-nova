import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TransactionsTab from '../TransactionsTab.vue'

function tx(overrides: Record<string, unknown> = {}) {
  return {
    transactionName: 'apply',
    input: { amount: 100 },
    executedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

describe('TransactionsTab', () => {
  it('renders the root data-testid', () => {
    const wrapper = mount(TransactionsTab, {
      props: { transactions: undefined, loading: false, error: null },
    })

    expect(wrapper.find('[data-testid="executions-transactions-tab"]').exists()).toBe(true)
  })

  it('renders a loading placeholder while loading', () => {
    const wrapper = mount(TransactionsTab, {
      props: { transactions: undefined, loading: true, error: null },
    })

    expect(wrapper.find('[data-testid="executions-transactions-loading"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Loading transactions')
  })

  it('renders an error banner when error is provided', () => {
    const wrapper = mount(TransactionsTab, {
      props: { transactions: undefined, loading: false, error: 'failed' },
    })

    expect(wrapper.find('[data-testid="error-banner"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('failed')
  })

  it('renders an empty placeholder when transactions are undefined', () => {
    const wrapper = mount(TransactionsTab, {
      props: { transactions: undefined, loading: false, error: null },
    })

    expect(wrapper.find('[data-testid="executions-transactions-empty"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No transactions recorded')
  })

  it('renders an empty placeholder when transactions are empty', () => {
    const wrapper = mount(TransactionsTab, {
      props: { transactions: [], loading: false, error: null },
    })

    expect(wrapper.find('[data-testid="executions-transactions-empty"]').exists()).toBe(true)
  })

  it('renders one row per transaction with name and formatted timestamp', () => {
    const wrapper = mount(TransactionsTab, {
      props: {
        transactions: [
          tx({ transactionName: 'first', executedAt: '2026-01-01T12:00:00Z' }),
          tx({ transactionName: 'second', executedAt: '2026-01-01T13:00:00Z' }),
        ],
        loading: false,
        error: null,
      },
    })

    const rows = wrapper.findAll('[data-testid="executions-transaction-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('first')
    expect(rows[0].text()).toContain(new Date('2026-01-01T12:00:00Z').toLocaleString())
    expect(rows[1].text()).toContain('second')
  })

  it('toggles the input JSON block when Show input is clicked', async () => {
    const wrapper = mount(TransactionsTab, {
      props: {
        transactions: [tx({ input: { amount: 42 } })],
        loading: false,
        error: null,
      },
    })

    expect(wrapper.find('[data-testid="executions-transaction-input"]').exists()).toBe(false)

    await wrapper.find('[data-testid="executions-transaction-toggle-input"]').trigger('click')

    const pre = wrapper.find('[data-testid="executions-transaction-input"]')
    expect(pre.exists()).toBe(true)
    expect(pre.text()).toContain('"amount": 42')

    await wrapper.find('[data-testid="executions-transaction-toggle-input"]').trigger('click')
    expect(wrapper.find('[data-testid="executions-transaction-input"]').exists()).toBe(false)
  })

  it('omits the toggle when input is undefined', () => {
    const wrapper = mount(TransactionsTab, {
      props: {
        transactions: [tx({ input: undefined })],
        loading: false,
        error: null,
      },
    })

    expect(wrapper.find('[data-testid="executions-transaction-toggle-input"]').exists()).toBe(false)
  })
})
