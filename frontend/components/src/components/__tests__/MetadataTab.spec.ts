import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { Execution } from '../../types/execution'
import MetadataTab from '../executions/MetadataTab.vue'

function makeExecution(overrides: Partial<Execution> = {}): Execution {
  return {
    id: 'exec-1',
    entity: 'Order',
    entityType: 'Process',
    mode: 'RUN',
    status: 'Completed',
    startedAt: '2026-07-30T10:00:00Z',
    correlationId: 'corr-abc',
    workflowId: 'wf-123',
    retries: 2,
    ...overrides,
  }
}

describe('MetadataTab', () => {
  it('renders the execution-derived metadata rows', () => {
    const execution = makeExecution()
    const wrapper = mount(MetadataTab, { props: { execution, metadata: undefined } })

    // The Correlation ID and Workflow ID cells also render T302 copy buttons;
    // assert the cell text starts with the id so the assertion stays stable
    // even when extra sibling controls are added inside the same <td>.
    const cells = wrapper.findAll('td').map((td) => td.text())
    expect(cells[0].startsWith('corr-abc')).toBe(true)
    expect(cells[1].startsWith('wf-123')).toBe(true)
    expect(cells.slice(2)).toEqual(['—', 'RUN', 'Process', '2'])
  })

  it('falls back to em-dash for missing correlationId, workflowId and triggeredBy', () => {
    const execution = makeExecution({
      correlationId: undefined,
      workflowId: undefined,
      triggeredBy: undefined,
    })
    const wrapper = mount(MetadataTab, { props: { execution, metadata: undefined } })

    const cells = wrapper.findAll('td').map((td) => td.text())
    expect(cells[0]).toBe('—')
    expect(cells[1]).toBe('—')
    expect(cells[2]).toBe('—')
    expect(cells.slice(3)).toEqual(['RUN', 'Process', '2'])
  })

  it('defaults retries to 0 when undefined', () => {
    const execution = makeExecution({ retries: undefined })
    const wrapper = mount(MetadataTab, { props: { execution, metadata: undefined } })

    const cells = wrapper.findAll('td').map((td) => td.text())
    expect(cells.at(-1)).toBe('0')
  })

  // T304: run attribution surfaced in the metadata tab.
  it('renders the triggeredBy value when present', () => {
    const execution = makeExecution({ triggeredBy: 'alice@example.com' })
    const wrapper = mount(MetadataTab, { props: { execution, metadata: undefined } })

    const row = wrapper.find('[data-testid="metadata-triggered-by"]')
    expect(row.exists()).toBe(true)
    expect(row.text()).toBe('alice@example.com')
  })

  it('uses the dedicated test id for the Triggered by row', () => {
    const execution = makeExecution({ triggeredBy: 'system' })
    const wrapper = mount(MetadataTab, { props: { execution, metadata: undefined } })

    expect(wrapper.find('[data-testid="metadata-triggered-by"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="executions-metadata-field-Triggered by"]').exists()).toBe(false)
  })

  it('flattens allowed metadata keys as additional rows', () => {
    const execution = makeExecution()
    const metadata = {
      version: '1.2.3',
      retrySettings: { maxAttempts: 3 },
      retryPolicy: 'exponential',
      ignoredKey: 'should-not-appear',
    }

    const wrapper = mount(MetadataTab, { props: { execution, metadata } })

    const keys = wrapper.findAll('th').map((th) => th.text())
    const values = wrapper.findAll('td').map((td) => td.text())

    expect(keys).toContain('version')
    expect(keys).toContain('retrySettings')
    expect(keys).toContain('retryPolicy')
    expect(keys).not.toContain('ignoredKey')

    expect(values).toContain('1.2.3')
    expect(values).toContain(JSON.stringify({ maxAttempts: 3 }))
    expect(values).toContain('exponential')
  })

  it('renders a caption and row scope attributes on the metadata table', () => {
    const wrapper = mount(MetadataTab, {
      props: { execution: makeExecution(), metadata: undefined },
    })

    expect(wrapper.find('caption').exists()).toBe(true)
    expect(wrapper.find('caption').classes()).toContain('sr-only')

    for (const th of wrapper.findAll('th')) {
      expect(th.attributes('scope')).toBe('row')
    }
  })

  // T302: Temporal Web UI deep-link behaviour on the Workflow ID row.
  describe('workflowLink prop', () => {
    const link = 'http://localhost:8233/namespaces/default/workflows/wf-123'

    it('renders the deep-link anchor when workflowLink is provided', () => {
      const wrapper = mount(MetadataTab, {
        props: { execution: makeExecution(), metadata: undefined, workflowLink: link },
      })

      const anchor = wrapper.find('[data-testid="temporal-workflow-link"]')
      expect(anchor.exists()).toBe(true)
      expect(anchor.attributes('href')).toBe(link)
      expect(anchor.attributes('target')).toBe('_blank')
      expect(anchor.attributes('rel')).toBe('noopener noreferrer')
    })

    it('omits the deep-link anchor when workflowLink is null', () => {
      const wrapper = mount(MetadataTab, {
        props: { execution: makeExecution(), metadata: undefined, workflowLink: null },
      })

      expect(wrapper.find('[data-testid="temporal-workflow-link"]').exists()).toBe(false)
      // workflowId text is still rendered
      expect(wrapper.text()).toContain('wf-123')
    })

    it('omits the deep-link anchor when execution has no workflowId, but still shows the copy button placeholder behaviour', () => {
      const wrapper = mount(MetadataTab, {
        props: {
          execution: makeExecution({ workflowId: undefined }),
          metadata: undefined,
          workflowLink: link,
        },
      })

      expect(wrapper.find('[data-testid="temporal-workflow-link"]').exists()).toBe(false)
      expect(wrapper.text()).toContain('—')
    })

    it('still renders the workflowId row when workflowLink is null', () => {
      const wrapper = mount(MetadataTab, {
        props: { execution: makeExecution(), metadata: undefined, workflowLink: null },
      })

      expect(
        wrapper
          .find('[data-testid="executions-metadata-field-Workflow ID"]')
          .exists(),
      ).toBe(true)
    })

    it('renders a copy-to-clipboard button next to the workflowId', () => {
      const wrapper = mount(MetadataTab, {
        props: { execution: makeExecution(), metadata: undefined, workflowLink: link },
      })

      const copyBtn = wrapper.find('[data-testid="workflow-id-copy"]')
      expect(copyBtn.exists()).toBe(true)
      expect(copyBtn.text()).toBe('Copy')
    })
  })
})
