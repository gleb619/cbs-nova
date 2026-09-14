import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ExplainDiffView from '../runner/ExplainDiffView.vue'

function mountExplainDiffView(props: Record<string, unknown>) {
  return mount(ExplainDiffView, { props: props as never })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ExplainDiffView', () => {
  it('shows placeholder when runOutput is undefined', () => {
    const wrapper = mountExplainDiffView({
      explainOutput: { status: 'ok' },
      runOutput: undefined,
    })

    expect(wrapper.text()).toContain('No run result to compare.')
    expect(wrapper.find('[data-testid="split-layout"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="unified-layout"]').exists()).toBe(false)
  })

  it('renders two JSON panes when both outputs are present', () => {
    const wrapper = mountExplainDiffView({
      explainOutput: { mode: 'explain', value: 1 },
      runOutput: { mode: 'run', value: 2 },
    })

    const explainPane = wrapper.find('[data-testid="explain-pane"]')
    const runPane = wrapper.find('[data-testid="run-pane"]')

    expect(explainPane.exists()).toBe(true)
    expect(runPane.exists()).toBe(true)
    expect(explainPane.text()).toContain('"mode": "explain"')
    expect(runPane.text()).toContain('"mode": "run"')
  })

  it('switches between split and unified layout via toggle button', async () => {
    const wrapper = mountExplainDiffView({
      explainOutput: { a: 1 },
      runOutput: { a: 2 },
    })

    expect(wrapper.find('[data-testid="split-layout"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="unified-layout"]').exists()).toBe(false)

    const toggleButton = wrapper.find('button')
    expect(toggleButton.text()).toBe('Unified')
    await toggleButton.trigger('click')

    expect(wrapper.find('[data-testid="split-layout"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="unified-layout"]').exists()).toBe(true)

    await wrapper.find('button').trigger('click')
    expect(wrapper.find('[data-testid="split-layout"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="unified-layout"]').exists()).toBe(false)
  })

  it('colours unified diff lines by kind', () => {
    const wrapper = mountExplainDiffView({
      explainOutput: { a: 1 },
      runOutput: { a: 1, b: 2 },
      layout: 'unified',
    })

    expect(wrapper.find('[data-testid="unified-layout"]').exists()).toBe(true)

    const lines = wrapper.findAll('[data-testid="diff-line"]')
    const classes = lines.map((line) => line.classes())

    expect(classes.some((c) => c.includes('bg-yellow-50'))).toBe(true)
    expect(classes.some((c) => c.includes('bg-green-50'))).toBe(true)
    expect(classes.some((c) => c.includes('border-transparent'))).toBe(true)
  })

  it('renders an empty explain pane without throwing when explainOutput is null', () => {
    const wrapper = mountExplainDiffView({
      explainOutput: null,
      runOutput: { a: 1 },
    })

    expect(wrapper.find('[data-testid="explain-pane"]').text()).toBe('')
  })

  it('renders an empty explain pane without throwing when explainOutput is undefined', () => {
    const wrapper = mountExplainDiffView({
      explainOutput: undefined,
      runOutput: { a: 1 },
    })

    expect(wrapper.find('[data-testid="explain-pane"]').text()).toBe('')
  })

  it('stamps data-testid on the root element', () => {
    const wrapper = mountExplainDiffView({
      explainOutput: { a: 1 },
      runOutput: { a: 2 },
    })

    expect(wrapper.find('[data-testid="explain-diff-view"]').exists()).toBe(true)
  })

  it('registers and removes the resize listener once per mount cycle', async () => {
    const addSpy = vi.spyOn(window, 'addEventListener')
    const removeSpy = vi.spyOn(window, 'removeEventListener')

    const countResize = (spy: ReturnType<typeof vi.spyOn>) =>
      spy.mock.calls.filter(([type]) => type === 'resize').length

    // First mount: +1 add, +0 remove.
    const wrapper = mountExplainDiffView({
      explainOutput: { a: 1 },
      runOutput: { a: 2 },
    })
    const addAfterMount = countResize(addSpy)
    const removeAfterMount = countResize(removeSpy)
    expect(addAfterMount).toBe(1)
    expect(removeAfterMount).toBe(0)

    // First unmount: +1 remove. add count stays at 1.
    wrapper.unmount()
    expect(countResize(addSpy)).toBe(addAfterMount)
    expect(countResize(removeSpy)).toBe(removeAfterMount + 1)

    // Remount: another +1 add, no new remove. The two mounts never overlap.
    const wrapper2 = mountExplainDiffView({
      explainOutput: { a: 1 },
      runOutput: { a: 2 },
    })
    expect(countResize(addSpy)).toBe(addAfterMount + 1)
    expect(countResize(removeSpy)).toBe(removeAfterMount + 1)

    wrapper2.unmount()
    expect(countResize(addSpy)).toBe(addAfterMount + 1)
    expect(countResize(removeSpy)).toBe(removeAfterMount + 2)
  })
})
