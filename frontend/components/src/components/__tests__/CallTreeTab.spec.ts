import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { CallNode } from '../../types/runner'
import CallTreeNode from '../runner/CallTreeNode.vue'
import CallTreeTab from '../runner/CallTreeTab.vue'
import ExternalCallsBadge from '../runner/ExternalCallsBadge.vue'

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

function mountCallTreeTab(props: Record<string, unknown>) {
  return mount(CallTreeTab, {
    props,
    global: { components: { CallTreeNode, ExternalCallsBadge } },
  })
}

describe('CallTreeTab', () => {
  it('shows a placeholder when tree is undefined', () => {
    const wrapper = mountCallTreeTab({ tree: undefined })
    expect(wrapper.text()).toContain('No call tree available.')
    expect(wrapper.findComponent(CallTreeNode).exists()).toBe(false)
  })

  it('renders a single root node with name, kind badge, and success icon', () => {
    const tree = makeNode({ name: 'StartProcess', kind: 'PROCESS', success: true })
    const wrapper = mountCallTreeTab({ tree })
    expect(wrapper.text()).toContain('StartProcess')
    expect(wrapper.text()).toContain('PROCESS')
    expect(wrapper.text()).toContain('✓')
    expect(wrapper.text()).not.toContain('✗')
  })

  it('renders child nodes when expanded', () => {
    const tree = makeNode({
      name: 'Parent',
      kind: 'PROCESS',
      children: [makeNode({ name: 'Child', kind: 'FUNCTION', success: true })],
    })
    const wrapper = mountCallTreeTab({ tree })
    expect(wrapper.text()).toContain('Child')
    expect(wrapper.text()).toContain('FUNCTION')
  })

  it('collapses a node on header click and shows the child count', async () => {
    const tree = makeNode({
      name: 'Parent',
      kind: 'PROCESS',
      children: [
        makeNode({ name: 'Child1', kind: 'HELPER' }),
        makeNode({ name: 'Child2', kind: 'TRANSACTION' }),
      ],
    })
    const wrapper = mountCallTreeTab({ tree })
    expect(wrapper.text()).toContain('Child1')
    expect(wrapper.text()).toContain('Child2')

    await wrapper.find('button').trigger('click')

    expect(wrapper.text()).not.toContain('Child1')
    expect(wrapper.text()).not.toContain('Child2')
    expect(wrapper.text()).toContain('2 children')
  })

  it('renders external calls via ExternalCallsBadge when present', () => {
    const tree = makeNode({
      name: 'WithExternals',
      kind: 'TRANSACTION',
      externalCalls: [
        { type: 'HTTP', target: 'api.example.com', operation: 'GET' },
        { type: 'DB', target: 'orders', operation: 'select' },
      ],
    })
    const wrapper = mountCallTreeTab({ tree })
    expect(wrapper.findComponent(ExternalCallsBadge).exists()).toBe(true)
    expect(wrapper.text()).toContain('[HTTP] api.example.com — GET')
    expect(wrapper.text()).toContain('[DB] orders — select')
  })

  it('applies the blue border class for PROCESS kind', () => {
    const tree = makeNode({ name: 'ProcessNode', kind: 'PROCESS' })
    const wrapper = mountCallTreeTab({ tree })
    const node = wrapper.findComponent(CallTreeNode)
    expect(node.classes()).toContain('border-blue-500')
  })

  it('stamps data-testid on the root, tree container, node, and toggle', () => {
    const tree = makeNode({
      name: 'Root',
      kind: 'PROCESS',
      children: [makeNode({ name: 'Child', kind: 'FUNCTION' })],
    })
    const wrapper = mountCallTreeTab({ tree })

    expect(wrapper.find('[data-testid="call-tree-tab"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="call-tree"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="call-tree-node"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="call-tree-node-toggle"]').exists()).toBe(true)
  })
})
