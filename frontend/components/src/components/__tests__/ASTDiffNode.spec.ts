import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ASTDiffNode as ASTDiffNodeData } from '../../composables/usePreviewDiff'
import ASTDiffNode from '../runner/ASTDiffNode.vue'

function makeNode(overrides: Partial<ASTDiffNodeData> = {}): ASTDiffNodeData {
  return {
    status: 'same',
    name: 'root',
    kind: 'PROCESS',
    success: true,
    children: [],
    propertyChanges: [],
    ...overrides,
  }
}

function mountNode(node: ASTDiffNodeData, depth?: number) {
  return mount(ASTDiffNode, {
    props: { node, depth },
    // ASTDiffNode recursively renders itself; register it globally so the
    // child template references resolve under jsdom/happy-dom.
    global: {
      components: { ASTDiffNode },
    },
  })
}

describe('ASTDiffNode', () => {
  it('renders the node kind, name and status icon at the root', () => {
    const wrapper = mountNode(makeNode({ status: 'modified', name: 'chargeOrder' }))

    const root = wrapper.find('[data-testid="ast-diff-node"]')
    expect(root.exists()).toBe(true)
    expect(root.attributes('data-status')).toBe('modified')

    const statusBadge = wrapper.find('[data-testid="ast-diff-status"]')
    expect(statusBadge.exists()).toBe(true)
    expect(statusBadge.text()).toBe('~')
    expect(statusBadge.attributes('aria-label')).toBe('modified')

    expect(wrapper.text()).toContain('PROCESS')
    expect(wrapper.text()).toContain('chargeOrder')
  })

  it.each<{
    status: ASTDiffNodeData['status']
    icon: string
    label: string
    borderClass: string
  }>([
    { status: 'same', icon: '=', label: 'unchanged', borderClass: 'border-gray-200' },
    { status: 'added', icon: '+', label: 'added', borderClass: 'border-green-400' },
    { status: 'removed', icon: '−', label: 'removed', borderClass: 'border-red-400' },
    { status: 'modified', icon: '~', label: 'modified', borderClass: 'border-yellow-400' },
  ])('maps status $status to icon/icon-label and border class', ({ status, icon, label, borderClass }) => {
    const wrapper = mountNode(makeNode({ status }))

    const statusBadge = wrapper.find('[data-testid="ast-diff-status"]')
    expect(statusBadge.text()).toBe(icon)
    expect(statusBadge.attributes('aria-label')).toBe(label)

    const root = wrapper.find('[data-testid="ast-diff-node"]')
    expect(root.classes().some((c) => c.includes(borderClass))).toBe(true)
  })

  it('applies an ml- indent class scaled by depth and capped at 16', () => {
    const wrapperShallow = mountNode(makeNode(), 0)
    expect(wrapperShallow.find('[data-testid="ast-diff-node"]').classes()).not.toContain('ml-0')

    const wrapperTwo = mountNode(makeNode(), 2)
    expect(wrapperTwo.find('[data-testid="ast-diff-node"]').classes()).toContain('ml-8')

    const wrapperSix = mountNode(makeNode(), 4)
    expect(wrapperSix.find('[data-testid="ast-diff-node"]').classes()).toContain('ml-16')

    // depth 5 would normally compute to ml-20, but it is capped at 16
    const wrapperDeep = mountNode(makeNode(), 5)
    const deepClasses = wrapperDeep.find('[data-testid="ast-diff-node"]').classes()
    expect(deepClasses).toContain('ml-16')
    expect(deepClasses).not.toContain('ml-20')
  })

  it('is expanded by default for depth <= 1 and collapsed for deeper nodes', async () => {
    const child = makeNode({ status: 'same', name: 'child' })

    const shallowWrapper = mountNode(makeNode({ children: [child] }), 0)
    expect(shallowWrapper.findAll('[data-testid="ast-diff-node"]')).toHaveLength(2)
    expect(shallowWrapper.findAll('[data-testid="ast-diff-status"]')).toHaveLength(2)

    const deepWrapper = mountNode(makeNode({ children: [child] }), 3)
    // root expanded (depth 3 <= 1 is false), child collapsed too — so only root renders
    expect(deepWrapper.findAll('[data-testid="ast-diff-node"]')).toHaveLength(1)

    // The collapsed root should show the child summary
    expect(deepWrapper.text()).toContain('1 child')
  })

  it('shows the child summary with correct pluralisation when collapsed', () => {
    const child1 = makeNode({ name: 'c1' })
    const child2 = makeNode({ name: 'c2' })

    const singleWrapper = mountNode(makeNode({ children: [child1] }), 3)
    expect(singleWrapper.text()).toContain('1 child')

    const pluralWrapper = mountNode(makeNode({ children: [child1, child2] }), 3)
    expect(pluralWrapper.text()).toContain('2 children')
  })

  it('does not show the child summary when there are no children', () => {
    const wrapper = mountNode(makeNode({ children: [] }), 3)
    expect(wrapper.text()).not.toContain('child')
  })

  it('toggles expand and collapse when the header is clicked', async () => {
    const child = makeNode({ name: 'c1' })
    const wrapper = mountNode(makeNode({ children: [child] }), 3)

    // initially collapsed (depth 3 > 1)
    expect(wrapper.findAll('[data-testid="ast-diff-node"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('1 child')

    const header = wrapper.find('button')
    await header.trigger('click')

    // expanded — child now renders
    expect(wrapper.findAll('[data-testid="ast-diff-node"]')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('1 child')

    await header.trigger('click')
    expect(wrapper.findAll('[data-testid="ast-diff-node"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('1 child')
  })

  it('renders property changes when expanded', async () => {
    const wrapper = mountNode(
      makeNode({
        status: 'modified',
        propertyChanges: [
          { key: 'input', lhs: { a: 1 }, rhs: { a: 2 } },
          { key: 'success', lhs: false, rhs: true },
        ],
      }),
      0,
    )

    const propertyChanges = wrapper.find('[data-testid="ast-diff-property-changes"]')
    expect(propertyChanges.exists()).toBe(true)
    const text = propertyChanges.text()
    expect(text).toContain('input:')
    expect(text).toContain('success:')
    expect(text).toContain('→')
    expect(text).toContain('{"a":1}')
    expect(text).toContain('{"a":2}')
  })

  it('omits the property-changes block when propertyChanges is empty', () => {
    const wrapper = mountNode(makeNode({ propertyChanges: [] }), 0)
    expect(wrapper.find('[data-testid="ast-diff-property-changes"]').exists()).toBe(false)
  })

  it('renders children recursively when expanded', async () => {
    const grandchild = makeNode({ status: 'removed', name: 'grandchild' })
    const child = makeNode({ status: 'added', name: 'child', children: [grandchild] })
    const wrapper = mountNode(makeNode({ children: [child] }), 0)

    // depth 0 → all expanded
    const nodes = wrapper.findAll('[data-testid="ast-diff-node"]')
    expect(nodes).toHaveLength(3)

    const statusBadges = wrapper.findAll('[data-testid="ast-diff-status"]')
    expect(statusBadges.map((b) => b.text())).toEqual(['=', '+', '−'])
  })
})