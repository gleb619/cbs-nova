import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { CallNode } from '../../types/runner'
import ExternalCallsTab from '../runner/ExternalCallsTab.vue'

function makeNode(overrides: Partial<CallNode> = {}): CallNode {
  return {
    name: 'Root',
    kind: 'PROCESS',
    success: true,
    children: [],
    externalCalls: [],
    ...overrides,
  }
}

function mountExternalCallsTab(props: Record<string, unknown>) {
  return mount(ExternalCallsTab, { props })
}

describe('ExternalCallsTab', () => {
  it('shows an empty placeholder when tree is undefined', () => {
    const wrapper = mountExternalCallsTab({ tree: undefined })
    expect(wrapper.text()).toContain('No external calls captured.')
  })

  it('shows an empty placeholder when the tree has no external calls', () => {
    const wrapper = mountExternalCallsTab({
      tree: makeNode({ name: 'NoCalls', kind: 'PROCESS', externalCalls: [] }),
    })
    expect(wrapper.text()).toContain('No external calls captured.')
  })

  it('renders a single external call with type badge, target, operation, timestamp, and source node', () => {
    const wrapper = mountExternalCallsTab({
      tree: makeNode({
        name: 'OrderProcess',
        kind: 'PROCESS',
        externalCalls: [
          {
            type: 'database',
            target: 'orders',
            operation: 'select',
            timestamp: 1721404800000,
          },
        ],
      }),
    })

    expect(wrapper.text()).toContain('database')
    expect(wrapper.text()).toContain('orders')
    expect(wrapper.text()).toContain('select')
    expect(wrapper.text()).toContain('[2024-07-19T16:00:00.000Z]')
    expect(wrapper.text()).toContain('PROCESS: OrderProcess')

    const badge = wrapper.find('span[data-type="database"]')
    expect(badge.exists()).toBe(true)
    expect(badge.classes()).toContain('bg-blue-50')
    expect(badge.classes()).toContain('text-blue-600')
  })

  it('flattens external calls from nested child nodes', () => {
    const wrapper = mountExternalCallsTab({
      tree: makeNode({
        name: 'Root',
        kind: 'PROCESS',
        externalCalls: [
          { type: 'http', target: 'api.example.com', operation: 'GET', timestamp: 1 },
        ],
        children: [
          makeNode({
            name: 'Child',
            kind: 'TRANSACTION',
            externalCalls: [
              { type: 'database', target: 'accounts', operation: 'insert', timestamp: 2 },
            ],
            children: [
              makeNode({
                name: 'Grandchild',
                kind: 'HELPER',
                externalCalls: [
                  { type: 'mq', target: 'events-bus', operation: 'publish', timestamp: 3 },
                ],
              }),
            ],
          }),
        ],
      }),
    })

    expect(wrapper.text()).toContain('api.example.com')
    expect(wrapper.text()).toContain('accounts')
    expect(wrapper.text()).toContain('events-bus')
    expect(wrapper.text()).toContain('PROCESS: Root')
    expect(wrapper.text()).toContain('TRANSACTION: Child')
    expect(wrapper.text()).toContain('HELPER: Grandchild')

    const items = wrapper.findAll('li')
    expect(items).toHaveLength(3)
  })

  it('renders multiple call types with the appropriate badge styling', () => {
    const wrapper = mountExternalCallsTab({
      tree: makeNode({
        name: 'Multi',
        kind: 'TRANSACTION',
        externalCalls: [
          {
            type: 'http',
            target: 'api.example.com',
            operation: 'GET',
            timestamp: 1,
          },
          {
            type: 'database',
            target: 'orders',
            operation: 'select',
            timestamp: 2,
          },
          {
            type: 'mq',
            target: 'events',
            operation: 'publish',
            timestamp: 3,
          },
          { type: 'unknown_kind', target: 'x', operation: 'y', timestamp: 4 },
        ],
      }),
    })

    const httpBadge = wrapper.find('span[data-type="http"]')
    expect(httpBadge.exists()).toBe(true)
    expect(httpBadge.classes()).toContain('bg-green-50')

    const dbBadge = wrapper.find('span[data-type="database"]')
    expect(dbBadge.exists()).toBe(true)
    expect(dbBadge.classes()).toContain('bg-blue-50')

    const mqBadge = wrapper.find('span[data-type="mq"]')
    expect(mqBadge.exists()).toBe(true)
    expect(mqBadge.classes()).toContain('bg-purple-50')

    const fallbackBadge = wrapper.find('span[data-type="unknown_kind"]')
    expect(fallbackBadge.exists()).toBe(true)
    expect(fallbackBadge.classes()).toContain('bg-gray-100')
  })

  it('displays the call count badge', () => {
    const wrapper = mountExternalCallsTab({
      tree: makeNode({
        name: 'TwoCalls',
        kind: 'PROCESS',
        externalCalls: [
          { type: 'http', target: 'a', operation: 'GET', timestamp: 1 },
          { type: 'database', target: 'b', operation: 'insert', timestamp: 2 },
        ],
      }),
    })

    expect(wrapper.text()).toContain('2 external calls')
  })

  it('uses singular wording for a single call', () => {
    const wrapper = mountExternalCallsTab({
      tree: makeNode({
        name: 'OneCall',
        kind: 'PROCESS',
        externalCalls: [
          { type: 'http', target: 'a', operation: 'GET', timestamp: 1 },
        ],
      }),
    })

    expect(wrapper.text()).toContain('1 external call')
    expect(wrapper.text()).not.toContain('1 external calls')
  })
})