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

    const cells = wrapper.findAll('td').map((td) => td.text())
    expect(cells).toEqual(['corr-abc', 'wf-123', 'RUN', 'Process', '2'])
  })

  it('falls back to em-dash for missing correlationId and workflowId', () => {
    const execution = makeExecution({ correlationId: undefined, workflowId: undefined })
    const wrapper = mount(MetadataTab, { props: { execution, metadata: undefined } })

    const cells = wrapper.findAll('td').map((td) => td.text())
    expect(cells).toEqual(['—', '—', 'RUN', 'Process', '2'])
  })

  it('defaults retries to 0 when undefined', () => {
    const execution = makeExecution({ retries: undefined })
    const wrapper = mount(MetadataTab, { props: { execution, metadata: undefined } })

    const cells = wrapper.findAll('td').map((td) => td.text())
    expect(cells.at(-1)).toBe('0')
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
})
