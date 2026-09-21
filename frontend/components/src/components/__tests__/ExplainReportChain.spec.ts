import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ExplainReportNode } from '../../types/runner'
import ExplainReportChain from '../runner/ExplainReportChain.vue'

const tree: ExplainReportNode = {
  name: 'UnreliableApiSuccess',
  description: '',
  markdown: '',
  children: [
    {
      name: 'unreliableApiTxResilient',
      description: '',
      markdown: '',
      children: [
        {
          name: 'unreliableApi',
          description: '',
          markdown: '',
          children: [],
        },
      ],
    },
  ],
}

describe('ExplainReportChain', () => {
  it('renders the primary root-to-leaf chain', () => {
    const wrapper = mount(ExplainReportChain, { props: { report: tree } })

    expect(wrapper.find('[data-testid="explain-report-chain"]').exists()).toBe(true)
    const nodes = wrapper.findAll('[data-testid="explain-report-chain-node"]')
    expect(nodes.map((n) => n.text())).toEqual([
      'UnreliableApiSuccess',
      'unreliableApiTxResilient',
      'unreliableApi',
    ])

    const arrows = wrapper.findAll('[data-testid="explain-report-chain-arrow"]')
    expect(arrows.length).toBe(2)
  })

  it('stops at a cycle', () => {
    const cyclic: ExplainReportNode = {
      name: 'A',
      description: '',
      markdown: '',
      children: [],
    }
    cyclic.children.push(cyclic)

    const wrapper = mount(ExplainReportChain, { props: { report: cyclic } })
    const nodes = wrapper.findAll('[data-testid="explain-report-chain-node"]')
    expect(nodes.map((n) => n.text())).toEqual(['A'])
    expect(wrapper.findAll('[data-testid="explain-report-chain-arrow"]').length).toBe(0)
  })

  it('renders only the root when there are no children', () => {
    const wrapper = mount(ExplainReportChain, {
      props: { report: { name: 'OnlyRoot', description: '', markdown: '', children: [] } },
    })

    const nodes = wrapper.findAll('[data-testid="explain-report-chain-node"]')
    expect(nodes.length).toBe(1)
    expect(nodes[0]?.text()).toBe('OnlyRoot')
  })
})
