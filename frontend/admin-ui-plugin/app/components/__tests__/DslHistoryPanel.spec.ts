import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DslHistoryPanel from '../DslHistoryPanel.vue'

const ENTRIES = [
  {
    timestamp: '200',
    timestampMillis: 200,
    sizeBytes: 120,
    lastModifiedMillis: 200,
  },
  {
    timestamp: '100',
    timestampMillis: 100,
    sizeBytes: 110,
    lastModifiedMillis: 100,
  },
]

function makeDiff(overrides: Record<string, unknown> = {}) {
  return {
    name: 'Demo',
    timestamp: '100',
    before: '{\n  "version" : "B"\n}',
    after: '{\n  "version" : "A"\n}',
    hunks: [
      {
        beforeStart: 0,
        beforeLines: 3,
        afterStart: 0,
        afterLines: 3,
        lines: [' {', '-  "version" : "B"', '+  "version" : "A"', ' }'],
      },
    ],
    truncated: false,
    ...overrides,
  }
}

function mountPanel(overrides: Record<string, unknown> = {}) {
  const props = {
    name: 'Demo',
    listHistory: vi.fn().mockResolvedValue(ENTRIES),
    getEntry: vi.fn().mockResolvedValue({ name: 'Demo', version: 'A' }),
    getDiff: vi.fn().mockResolvedValue(makeDiff()),
    restore: vi.fn().mockResolvedValue({ ok: true }),
    ...overrides,
  }
  const wrapper = mount(DslHistoryPanel, { props })
  return { wrapper, props }
}

describe('DslHistoryPanel', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('lists history entries for the construct', async () => {
    const { wrapper, props } = mountPanel()
    await flushPromises()

    expect(props.listHistory).toHaveBeenCalledWith('Demo')
    const rows = wrapper.findAll('[data-testid="history-entry-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].attributes('data-timestamp')).toBe('200')
  })

  it('clicking a row fetches the entry content and the diff', async () => {
    const { wrapper, props } = mountPanel()
    await flushPromises()

    await wrapper.find('[data-testid="history-entry-row"][data-timestamp="100"]').trigger('click')
    await flushPromises()

    expect(props.getEntry).toHaveBeenCalledWith('Demo', '100')
    expect(props.getDiff).toHaveBeenCalledWith('Demo', '100')
    const diffView = wrapper.find('[data-testid="history-diff-view"]')
    expect(diffView.exists()).toBe(true)
    expect(diffView.findAll('[data-testid="preview-diff-line"]').length).toBeGreaterThan(0)
  })

  it('blocks restore until the diff has been shown, then requires an explicit confirm', async () => {
    const { wrapper, props } = mountPanel()
    await flushPromises()

    // No restore control at all before an entry is selected and diffed.
    expect(wrapper.find('[data-testid="history-restore-button"]').exists()).toBe(false)

    await wrapper.find('[data-testid="history-entry-row"][data-timestamp="100"]').trigger('click')
    await flushPromises()

    // First click only arms the confirm step — no restore call yet.
    await wrapper.find('[data-testid="history-restore-button"]').trigger('click')
    expect(props.restore).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="history-restore-confirm"]').exists()).toBe(true)

    // Confirm executes the restore.
    await wrapper.find('[data-testid="history-restore-confirm"]').trigger('click')
    await flushPromises()
    expect(props.restore).toHaveBeenCalledWith('Demo', '100')
    expect(wrapper.emitted('restored')).toBeTruthy()
  })

  it('keeps restore blocked when the diff fails to load', async () => {
    const { wrapper } = mountPanel({
      getDiff: vi.fn().mockRejectedValue(new Error('boom')),
    })
    await flushPromises()
    await wrapper.find('[data-testid="history-entry-row"][data-timestamp="100"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="history-detail-error"]').exists()).toBe(true)
    // Restore stays disabled — no diff has been shown.
    expect(
      wrapper.find('[data-testid="history-restore-button"]').attributes('disabled'),
    ).toBeDefined()
  })

  it('cancel disarms the confirm step without restoring', async () => {
    const { wrapper, props } = mountPanel()
    await flushPromises()
    await wrapper.find('[data-testid="history-entry-row"][data-timestamp="100"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-testid="history-restore-button"]').trigger('click')
    await wrapper.find('[data-testid="history-restore-cancel"]').trigger('click')

    expect(props.restore).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="history-restore-confirm"]').exists()).toBe(false)
  })

  it('shows a notice instead of a diff when no published baseline exists', async () => {
    const { wrapper } = mountPanel({
      getDiff: vi.fn().mockResolvedValue(makeDiff({ before: null })),
    })
    await flushPromises()
    await wrapper.find('[data-testid="history-entry-row"][data-timestamp="100"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="history-no-published"]').exists()).toBe(true)
    // Restore still possible after the (empty) diff view is shown.
    expect(
      wrapper.find('[data-testid="history-restore-button"]').attributes('disabled'),
    ).toBeUndefined()
  })

  it('surfaces a detail error when the diff fetch fails', async () => {
    const { wrapper } = mountPanel({
      getDiff: vi.fn().mockRejectedValue(new Error('boom')),
    })
    await flushPromises()
    await wrapper.find('[data-testid="history-entry-row"][data-timestamp="100"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="history-detail-error"]').text()).toContain('boom')
  })

  it('loads entries once per mount and does not react to in-place name changes', async () => {
    const { wrapper, props } = mountPanel()
    await flushPromises()

    expect(props.listHistory).toHaveBeenCalledTimes(1)
    expect(props.listHistory).toHaveBeenCalledWith('Demo')

    // The parent remounts the panel (via :key) when the construct changes,
    // so an in-place prop update must not trigger a reload.
    await wrapper.setProps({ name: 'Other' })
    await flushPromises()

    expect(props.listHistory).toHaveBeenCalledTimes(1)
  })

  it('reloads entries when remounted for another construct', async () => {
    const first = mountPanel()
    await flushPromises()
    expect(first.props.listHistory).toHaveBeenCalledWith('Demo')
    first.wrapper.unmount()

    const second = mountPanel({ name: 'Other', listHistory: vi.fn().mockResolvedValue([]) })
    await flushPromises()
    expect(second.props.listHistory).toHaveBeenCalledWith('Other')
    second.wrapper.unmount()
  })
})
