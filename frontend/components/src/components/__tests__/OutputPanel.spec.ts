import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { RunnerOutput } from '../../types/runner'
import CallTreeNode from '../runner/CallTreeNode.vue'
import CallTreeTab from '../runner/CallTreeTab.vue'
import DryRunLogsTab from '../runner/DryRunLogsTab.vue'
import ErrorsTab from '../runner/ErrorsTab.vue'
import ExplainDiffView from '../runner/ExplainDiffView.vue'
import ExplainOutput from '../runner/ExplainOutput.vue'
import ExternalCallsBadge from '../runner/ExternalCallsBadge.vue'
import MetadataTab from '../runner/MetadataTab.vue'
import OutputPanel from '../runner/OutputPanel.vue'
import ResultTab from '../runner/ResultTab.vue'

function makeOutput(overrides: Partial<RunnerOutput> = {}): RunnerOutput {
  return {
    result: { ok: true },
    metadata: { key: 'value' },
    errors: [{ message: 'oops' }],
    description: 'A description',
    mermaidDiagram: 'graph TD; A-->B',
    ...overrides,
  }
}

function mountOutputPanel(props: Record<string, unknown>) {
  return mount(OutputPanel, {
    props,
    global: {
      components: {
        ExplainOutput,
        ResultTab,
        MetadataTab,
        ErrorsTab,
        CallTreeTab,
        CallTreeNode,
        ExternalCallsBadge,
        DryRunLogsTab,
        ExplainDiffView,
      },
    },
  })
}

describe('OutputPanel', () => {
  it('renders ResultTab by default when output is present in run mode', () => {
    const wrapper = mountOutputPanel({
      output: makeOutput(),
      mode: 'run',
      status: 'success',
    })

    expect(wrapper.findComponent(ResultTab).exists()).toBe(true)
    expect(wrapper.findComponent(MetadataTab).exists()).toBe(false)
    expect(wrapper.findComponent(ErrorsTab).exists()).toBe(false)
  })

  it('switches to MetadataTab when the Metadata tab is clicked', async () => {
    const wrapper = mountOutputPanel({
      output: makeOutput(),
      mode: 'run',
      status: 'success',
    })

    const metadataButton = wrapper.findAll('button').find((b) => b.text() === 'Metadata')
    expect(metadataButton).toBeDefined()
    await metadataButton?.trigger('click')

    expect(wrapper.findComponent(MetadataTab).exists()).toBe(true)
    expect(wrapper.findComponent(ResultTab).exists()).toBe(false)
  })

  it('switches to ErrorsTab when the Errors tab is clicked', async () => {
    const wrapper = mountOutputPanel({
      output: makeOutput(),
      mode: 'run',
      status: 'success',
    })

    const errorsButton = wrapper.findAll('button').find((b) => b.text() === 'Errors')
    expect(errorsButton).toBeDefined()
    await errorsButton?.trigger('click')

    expect(wrapper.findComponent(ErrorsTab).exists()).toBe(true)
    expect(wrapper.findComponent(ResultTab).exists()).toBe(false)
  })

  it('renders three tab buttons with correct labels and toggles active styling', async () => {
    const wrapper = mountOutputPanel({
      output: makeOutput(),
      mode: 'run',
      status: 'success',
    })

    const buttons = wrapper.findAll('button')
    expect(buttons.map((b) => b.text())).toEqual(['Result', 'Metadata', 'Errors'])

    const resultButton = buttons.find((b) => b.text() === 'Result')
    const metadataButton = buttons.find((b) => b.text() === 'Metadata')
    const errorsButton = buttons.find((b) => b.text() === 'Errors')
    expect(resultButton).toBeDefined()
    expect(metadataButton).toBeDefined()
    expect(errorsButton).toBeDefined()

    expect(resultButton.classes()).toContain('border-blue-600')
    expect(metadataButton.classes()).not.toContain('border-blue-600')
    expect(errorsButton.classes()).not.toContain('border-blue-600')

    await metadataButton.trigger('click')
    expect(resultButton.classes()).not.toContain('border-blue-600')
    expect(metadataButton.classes()).toContain('border-blue-600')
    expect(errorsButton.classes()).not.toContain('border-blue-600')
  })

  it('renders ExplainOutput with description and mermaid diagram in explain mode', () => {
    const wrapper = mountOutputPanel({
      output: makeOutput(),
      mode: 'explain',
      status: 'success',
    })

    const explain = wrapper.findComponent(ExplainOutput)
    expect(explain.exists()).toBe(true)
    expect(explain.props('description')).toBe('A description')
    expect(explain.props('mermaidDiagram')).toBe('graph TD; A-->B')
  })

  it('renders a placeholder and hides tabs and children when output is null', () => {
    const wrapper = mountOutputPanel({
      output: null,
      mode: 'run',
      status: 'idle',
    })

    expect(wrapper.text()).toContain('Output will appear here after running.')
    expect(wrapper.findAll('button')).toHaveLength(0)
    expect(wrapper.findComponent(ResultTab).exists()).toBe(false)
    expect(wrapper.findComponent(MetadataTab).exists()).toBe(false)
    expect(wrapper.findComponent(ErrorsTab).exists()).toBe(false)
  })

  it('renders the tab bar and active child when output is non-null', () => {
    const wrapper = mountOutputPanel({
      output: makeOutput(),
      mode: 'run',
      status: 'success',
    })

    expect(wrapper.findAll('button')).toHaveLength(3)
    expect(wrapper.findComponent(ResultTab).exists()).toBe(true)
  })

  it('does not throw when nested output fields are undefined and child tabs receive nullable props', () => {
    expect(() =>
      mountOutputPanel({
        output: makeOutput({
          result: undefined,
          metadata: undefined,
          errors: undefined,
        }),
        mode: 'run',
        status: 'success',
      }),
    ).not.toThrow()

    const wrapper = mountOutputPanel({
      output: makeOutput({
        result: undefined,
        metadata: undefined,
        errors: undefined,
      }),
      mode: 'run',
      status: 'success',
    })

    expect(wrapper.findComponent(ResultTab).exists()).toBe(true)
    expect(wrapper.findComponent(ResultTab).props('result')).toBeUndefined()
  })

  it('shows the Call Tree tab only in preview mode and switches to it', async () => {
    const runWrapper = mountOutputPanel({
      output: makeOutput(),
      mode: 'run',
      status: 'success',
    })
    expect(runWrapper.findAll('button').map((b) => b.text())).not.toContain('Call Tree')
    expect(runWrapper.findComponent(CallTreeTab).exists()).toBe(false)

    const previewWrapper = mountOutputPanel({
      output: makeOutput({
        astTree: {
          name: 'PreviewRoot',
          kind: 'PROCESS',
          success: true,
          children: [],
          externalCalls: [],
        },
      }),
      mode: 'preview',
      status: 'success',
    })
    const buttons = previewWrapper.findAll('button')
    expect(buttons.map((b) => b.text())).toContain('Call Tree')

    const callTreeButton = buttons.find((b) => b.text() === 'Call Tree')
    expect(callTreeButton).toBeDefined()
    await callTreeButton?.trigger('click')
    expect(previewWrapper.findComponent(CallTreeTab).exists()).toBe(true)
    expect(previewWrapper.findComponent(ResultTab).exists()).toBe(false)
  })

  it('shows the Logs tab only in preview/explain mode and switches to it', async () => {
    const runWrapper = mountOutputPanel({
      output: makeOutput(),
      mode: 'run',
      status: 'success',
    })
    expect(runWrapper.findAll('button').map((b) => b.text())).not.toContain('Logs')
    expect(runWrapper.findComponent(DryRunLogsTab).exists()).toBe(false)

    const previewWrapper = mountOutputPanel({
      output: makeOutput({
        dryRunLogs: [
          {
            timestamp: '2026-07-19T10:00:00Z',
            level: 'INFO',
            logger: 'com.cbs.Preview',
            message: 'Preview log',
          },
        ],
      }),
      mode: 'preview',
      status: 'success',
    })
    const buttons = previewWrapper.findAll('button')
    expect(buttons.map((b) => b.text())).toContain('Logs')

    const logsButton = buttons.find((b) => b.text() === 'Logs')
    expect(logsButton).toBeDefined()
    await logsButton?.trigger('click')
    expect(previewWrapper.findComponent(DryRunLogsTab).exists()).toBe(true)
    expect(previewWrapper.findComponent(ResultTab).exists()).toBe(false)

    const dryRunLogsTab = previewWrapper.findComponent(DryRunLogsTab)
    expect(dryRunLogsTab.props('logs')).toEqual([
      {
        timestamp: '2026-07-19T10:00:00Z',
        level: 'INFO',
        logger: 'com.cbs.Preview',
        message: 'Preview log',
      },
    ])
  })

  it('shows the Logs tab in explain mode', async () => {
    const wrapper = mountOutputPanel({
      output: makeOutput({
        dryRunLogs: [
          {
            timestamp: '2026-07-19T10:00:00Z',
            level: 'INFO',
            logger: 'com.cbs.Explain',
            message: 'Explain log',
          },
        ],
      }),
      mode: 'explain',
      status: 'success',
    })
    expect(wrapper.findAll('button').map((b) => b.text())).toContain('Logs')

    const logsButton = wrapper.findAll('button').find((b) => b.text() === 'Logs')
    expect(logsButton).toBeDefined()
    await logsButton?.trigger('click')

    const dryRunLogsTab = wrapper.findComponent(DryRunLogsTab)
    expect(dryRunLogsTab.exists()).toBe(true)
    expect(dryRunLogsTab.props('logs')).toEqual([
      {
        timestamp: '2026-07-19T10:00:00Z',
        level: 'INFO',
        logger: 'com.cbs.Explain',
        message: 'Explain log',
      },
    ])
  })
})
