import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ExplainReportNode } from '../../types/runner'
import ExplainReportList from '../runner/ExplainReportList.vue'

const tree: ExplainReportNode = {
  name: 'UnreliableApiSuccess',
  description: 'root summary',
  markdown: 'Root body',
  children: [
    {
      name: 'unreliableApiTxResilient',
      description: 'tx summary',
      markdown: 'tx body',
      children: [
        {
          name: 'unreliableApi',
          description: 'helper summary',
          markdown: 'helper body',
          children: [],
        },
      ],
    },
  ],
}

const branchingTree: ExplainReportNode = {
  name: 'Root',
  description: 'root',
  markdown: '',
  children: [
    {
      name: 'A',
      description: 'a',
      markdown: '',
      children: [
        { name: 'A1', description: 'a1', markdown: 'A1 body', children: [] },
        { name: 'A2', description: 'a2', markdown: 'A2 body', children: [] },
      ],
    },
    {
      name: 'B',
      description: 'b',
      markdown: 'B body',
      children: [],
    },
  ],
}

describe('ExplainReportList', () => {
  it('renders root as the first section with only expand/collapse-all controls', () => {
    const wrapper = mount(ExplainReportList, { props: { report: tree } })

    const sections = wrapper.findAll('[data-testid="explain-report-section"]')
    expect(sections.length).toBe(3)

    const rootHeading = sections[0]?.find('[data-testid="explain-report-section-heading"]')
    expect(rootHeading?.text()).toContain('UnreliableApiSuccess')

    expect(sections[0]?.find('[data-testid="explain-report-section-depth"]').text()).toBe('root')
    expect(sections[0]?.find('[data-testid="explain-report-expand-all"]').exists()).toBe(true)
    expect(sections[0]?.find('[data-testid="explain-report-collapse-all"]').exists()).toBe(true)
    expect(sections[0]?.find('[data-testid="explain-report-section-toggle"]').exists()).toBe(false)
  })

  it('renders every node as a flat vertical section', () => {
    const wrapper = mount(ExplainReportList, { props: { report: tree } })

    const headings = wrapper.findAll('[data-testid="explain-report-section-heading"]')
    expect(headings.map((h) => h.text().trim())).toEqual([
      'UnreliableApiSuccess',
      'unreliableApiTxResilient',
      'unreliableApi',
    ])

    const depths = wrapper.findAll('[data-testid="explain-report-section-depth"]')
    expect(depths.map((d) => d.text())).toEqual(['root', '+1', '+2'])
  })

  it('renders root name exactly once', () => {
    const wrapper = mount(ExplainReportList, { props: { report: tree } })
    const matches = wrapper.text().split('UnreliableApiSuccess').length - 1
    expect(matches).toBe(1)
  })

  it('descriptions are collapsed by default and use the same description panel', () => {
    const wrapper = mount(ExplainReportList, { props: { report: tree } })

    const descriptions = wrapper.findAll('[data-testid="explain-report-section-description"]')
    expect(descriptions.length).toBe(3)
    for (const desc of descriptions) {
      expect(desc.text()).toContain('Show details')
      expect(desc.text()).not.toContain('Hide details')
    }
  })

  it('expands root and non-root descriptions independently', async () => {
    const wrapper = mount(ExplainReportList, { props: { report: tree } })
    const descriptions = wrapper.findAll('[data-testid="explain-report-section-description"]')

    const rootButton = descriptions[0]?.find('button')
    await rootButton?.trigger('click')
    await wrapper.vm.$nextTick()

    expect(descriptions[0]?.text()).toContain('root summary')
    expect(descriptions[0]?.text()).toContain('Hide details')
    expect(descriptions[1]?.text()).toContain('Show details')

    const txButton = descriptions[1]?.find('button')
    await txButton?.trigger('click')
    await wrapper.vm.$nextTick()

    expect(descriptions[1]?.text()).toContain('tx summary')
    expect(descriptions[1]?.text()).toContain('Hide details')
  })

  it('collapsing a parent hides its body and collapses descendants while keeping all headlines visible', async () => {
    const wrapper = mount(ExplainReportList, { props: { report: tree } })

    expect(wrapper.findAll('[data-testid="explain-report-section"]').length).toBe(3)
    expect(wrapper.findAll('[data-testid="explain-report-section-body"]').length).toBe(3)

    const txToggle = wrapper.findAll('[data-testid="explain-report-section-toggle"]').at(0)
    await txToggle?.trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('[data-testid="explain-report-section"]').length).toBe(3)
    expect(wrapper.text()).toContain('UnreliableApiSuccess')
    expect(wrapper.text()).toContain('unreliableApiTxResilient')
    expect(wrapper.text()).toContain('unreliableApi')

    const bodies = wrapper.findAll('[data-testid="explain-report-section-body"]')
    expect(bodies.length).toBe(1)
    expect(bodies[0]?.text()).toContain('Root body')
  })

  it('expanding a parent does not expand its descendants', async () => {
    const wrapper = mount(ExplainReportList, { props: { report: branchingTree } })

    const toggles = wrapper.findAll('[data-testid="explain-report-section-toggle"]')
    await toggles[0]?.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('A')
    expect(wrapper.text()).not.toContain('A1 body')
    expect(wrapper.text()).not.toContain('A2 body')
    expect(wrapper.text()).toContain('B')

    await toggles[0]?.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('A')
    expect(wrapper.text()).toContain('A1')
    expect(wrapper.text()).toContain('A2')
    expect(wrapper.text()).toContain('B')
    expect(wrapper.text()).not.toContain('A1 body')
    expect(wrapper.text()).not.toContain('A2 body')
  })

  it('leaf toggle collapses and expands its own body', async () => {
    const wrapper = mount(ExplainReportList, { props: { report: tree } })

    expect(wrapper.findAll('[data-testid="explain-report-section-body"]').length).toBe(3)

    const toggles = wrapper.findAll('[data-testid="explain-report-section-toggle"]')
    const leafToggle = toggles[toggles.length - 1]
    await leafToggle?.trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('[data-testid="explain-report-section-body"]').length).toBe(2)
    expect(wrapper.text()).not.toContain('helper body')

    await leafToggle?.trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('[data-testid="explain-report-section-body"]').length).toBe(3)
    expect(wrapper.text()).toContain('helper body')
  })

  it('root collapse/expand all buttons toggle all bodies', async () => {
    const wrapper = mount(ExplainReportList, { props: { report: tree } })

    await wrapper.find('[data-testid="explain-report-collapse-all"]').trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('[data-testid="explain-report-section"]').length).toBe(3)
    expect(wrapper.findAll('[data-testid="explain-report-section-body"]').length).toBe(0)

    await wrapper.find('[data-testid="explain-report-expand-all"]').trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('[data-testid="explain-report-section-body"]').length).toBe(3)
  })

  it('renders a non-root leaf node with a toggle', () => {
    const wrapper = mount(ExplainReportList, {
      props: {
        report: {
          name: 'root',
          description: '',
          markdown: '',
          children: [{ name: 'leaf', description: 'only desc', markdown: '', children: [] }],
        },
      },
    })

    expect(wrapper.find('[data-testid="explain-report-section"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="explain-report-section-heading"]').text()).toContain('root')
    expect(
      wrapper.findAll('[data-testid="explain-report-section-toggle"]').length,
    ).toBeGreaterThanOrEqual(1)

    const sections = wrapper.findAll('[data-testid="explain-report-section"]')
    const leaf = sections[sections.length - 1]
    expect(leaf?.find('[data-testid="explain-report-section-heading"]').text()).toContain('leaf')
    expect(leaf?.find('[data-testid="explain-report-section-toggle"]').exists()).toBe(true)
  })
})
