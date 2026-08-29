import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { CallNode } from '../../types/runner'
import CallTreeNode from '../runner/CallTreeNode.vue'
import ExternalCallsBadge from '../runner/ExternalCallsBadge.vue'

function node(overrides: Partial<CallNode> = {}): CallNode {
  return {
    name: 'N',
    kind: 'PROCESS',
    success: true,
    children: [],
    externalCalls: [],
    ...overrides,
  }
}

function mountCallTreeNode(props: Record<string, unknown>) {
  return mount(CallTreeNode, {
    props,
    global: { components: { ExternalCallsBadge } },
  })
}

describe('CallTreeNode', () => {
  it('renders the root data-testid, kind badge, name and success icon', () => {
    const wrapper = mountCallTreeNode({
      node: node({ name: 'Root', kind: 'PROCESS', success: true }),
      depth: 0,
    })

    expect(wrapper.find('[data-testid="call-tree-node"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Root')
    expect(wrapper.text()).toContain('PROCESS')
    expect(wrapper.text()).toContain('✓')
  })

  it('renders the failure icon when success is false', () => {
    const wrapper = mountCallTreeNode({
      node: node({ success: false }),
      depth: 0,
    })

    expect(wrapper.text()).toContain('✗')
  })

  it.each([
    ['PROCESS', 'border-blue-500', 'bg-blue-100'],
    ['TRANSACTION', 'border-green-500', 'bg-green-100'],
    ['HELPER', 'border-purple-500', 'bg-purple-100'],
    ['FUNCTION', 'border-yellow-500', 'bg-yellow-100'],
  ])('applies %s kind styling', (kind, borderClass, badgeClass) => {
    const wrapper = mountCallTreeNode({
      node: node({ kind: kind as CallNode['kind'] }),
      depth: 0,
    })

    expect(wrapper.find('[data-testid="call-tree-node"]').classes()).toContain(borderClass)
    expect(wrapper.find('button span.uppercase').classes()).toContain(badgeClass)
  })

  it('adds the correct indentation class and caps it at ml-16', () => {
    const shallow = mountCallTreeNode({ node: node(), depth: 0 })
    expect(shallow.find('[data-testid="call-tree-node"]').classes()).not.toContain('ml-4')

    const one = mountCallTreeNode({ node: node(), depth: 1 })
    expect(one.find('[data-testid="call-tree-node"]').classes()).toContain('ml-4')

    const deep = mountCallTreeNode({ node: node(), depth: 5 })
    expect(deep.find('[data-testid="call-tree-node"]').classes()).toContain('ml-16')
  })

  it('is open by default for depth 0 and closed for depth 2', () => {
    const openWrapper = mountCallTreeNode({
      node: node({ name: 'Root', children: [node({ name: 'Child' })] }),
      depth: 0,
    })
    expect(openWrapper.text()).toContain('Child')
    expect(openWrapper.text()).not.toContain('child')

    const closedWrapper = mountCallTreeNode({
      node: node({ name: 'Root', children: [node({ name: 'Child' })] }),
      depth: 2,
    })
    expect(closedWrapper.text()).not.toContain('Child')
    expect(closedWrapper.text()).toContain('1 child')
  })

  it('toggles open and closed when the button is clicked', async () => {
    const wrapper = mountCallTreeNode({
      node: node({ name: 'Deep', children: [node({ name: 'Hidden' })] }),
      depth: 2,
    })

    const button = wrapper.find('[data-testid="call-tree-node-toggle"]')
    expect(wrapper.text()).not.toContain('Hidden')

    await button.trigger('click')
    expect(wrapper.text()).toContain('Hidden')
    expect(wrapper.text()).not.toContain('1 child')

    await button.trigger('click')
    expect(wrapper.text()).not.toContain('Hidden')
    expect(wrapper.text()).toContain('1 child')
  })

  it('renders external calls in the summary when closed and as badges when open', async () => {
    const wrapper = mountCallTreeNode({
      node: node({
        name: 'Root',
        externalCalls: [{ target: 'A', operation: 'op' }],
      }),
      depth: 2,
    })

    expect(wrapper.text()).toContain('1 external call')
    expect(wrapper.find('[data-testid="external-calls-badge"]').exists()).toBe(false)

    await wrapper.find('[data-testid="call-tree-node-toggle"]').trigger('click')

    expect(wrapper.find('[data-testid="external-calls-badge"]').exists()).toBe(true)
  })

  it('pluralizes child and external call counts', () => {
    const wrapper = mountCallTreeNode({
      node: node({
        name: 'Root',
        externalCalls: [{}, {}],
        children: [node(), node()],
      }),
      depth: 2,
    })

    expect(wrapper.text()).toContain('2 children')
    expect(wrapper.text()).toContain('2 external calls')
  })

  it('recursively renders nested children when open', () => {
    const wrapper = mountCallTreeNode({
      node: node({
        name: 'Root',
        children: [
          node({
            name: 'Child',
            children: [node({ name: 'Grandchild' })],
          }),
        ],
      }),
      depth: 0,
    })

    const nodes = wrapper.findAll('[data-testid="call-tree-node"]')
    expect(nodes.length).toBeGreaterThanOrEqual(3)
    expect(wrapper.text()).toContain('Child')
    expect(wrapper.text()).toContain('Grandchild')
  })
})
