import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { DefinitionTestCase, DefinitionTestRunReport } from '../../types/dsl'
import DefinitionTestsPanel from '../dsl/DefinitionTestsPanel.vue'

const fixtureCases: DefinitionTestCase[] = [
  { caseName: 'happy path', input: { body: { x: 1 } }, expectedOutput: { result: 1 } },
  { caseName: 'edge case', input: { body: { x: 2 } }, expectedOutput: { result: 3 } },
]

function makeReport(): DefinitionTestRunReport {
  return {
    total: 3,
    passed: 1,
    failed: 1,
    errored: 1,
    cases: [
      {
        name: 'happy path',
        status: 'PASS',
        actual: { result: 1 },
        expected: { result: 1 },
        durationMs: 12,
      },
      {
        name: 'edge case',
        status: 'FAIL',
        actual: { result: 4 },
        expected: { result: 3 },
        durationMs: 34,
      },
      {
        name: 'broken case',
        status: 'ERROR',
        actual: null,
        expected: { result: 0 },
        durationMs: 56,
        diagnostics: { message: 'helper exploded' },
      },
    ],
  }
}

function mountPanel(
  overrides: {
    fetchTests?: ReturnType<typeof vi.fn>
    saveTests?: ReturnType<typeof vi.fn>
    runTests?: ReturnType<typeof vi.fn>
  } = {},
) {
  const fetchTests = overrides.fetchTests ?? vi.fn().mockResolvedValue(fixtureCases)
  const saveTests = overrides.saveTests ?? vi.fn().mockResolvedValue({ ok: true })
  const runTests = overrides.runTests ?? vi.fn().mockResolvedValue(makeReport())
  const wrapper = mount(DefinitionTestsPanel, {
    props: { name: 'OrderProcess', fetchTests, saveTests, runTests },
  })
  return { wrapper, fetchTests, saveTests, runTests }
}

describe('DefinitionTestsPanel', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('loads the stored cases on mount and renders one row per case', async () => {
    const { wrapper, fetchTests } = mountPanel()
    await flushPromises()

    expect(fetchTests).toHaveBeenCalledWith('OrderProcess')
    const rows = wrapper.findAll('[data-testid="tests-case-row"]')
    expect(rows).toHaveLength(2)
    const names = wrapper.findAll('[data-testid="tests-case-name"]')
    expect(names[0].element as HTMLInputElement).toHaveProperty('value', 'happy path')
    expect(names[1].element as HTMLInputElement).toHaveProperty('value', 'edge case')
    const inputs = wrapper.findAll('[data-testid="tests-case-input"]')
    expect((inputs[0].element as HTMLTextAreaElement).value).toBe(
      JSON.stringify(fixtureCases[0].input, null, 2),
    )
  })

  it('adds an empty case row on demand', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    await wrapper.find('[data-testid="tests-add-case"]').trigger('click')

    expect(wrapper.findAll('[data-testid="tests-case-row"]')).toHaveLength(3)
    expect(
      (wrapper.findAll('[data-testid="tests-case-name"]')[2].element as HTMLInputElement).value,
    ).toBe('')
  })

  it('removes the targeted case row', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    await wrapper.findAll('[data-testid="tests-case-remove"]')[1].trigger('click')

    expect(wrapper.findAll('[data-testid="tests-case-row"]')).toHaveLength(1)
    expect(
      (wrapper.find('[data-testid="tests-case-name"]').element as HTMLInputElement).value,
    ).toBe('happy path')
  })

  it('gates Save on dirty state and clears it after a successful save', async () => {
    const { wrapper, saveTests } = mountPanel()
    await flushPromises()

    const saveButton = wrapper.find('[data-testid="tests-save-button"]')
    expect(saveButton.attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="tests-unsaved-hint"]').exists()).toBe(false)

    await wrapper.find('[data-testid="tests-case-name"]').setValue('renamed case')
    expect(saveButton.attributes('disabled')).toBeUndefined()
    expect(wrapper.find('[data-testid="tests-unsaved-hint"]').exists()).toBe(true)

    await saveButton.trigger('click')
    await flushPromises()

    expect(saveTests).toHaveBeenCalledTimes(1)
    expect(saveTests).toHaveBeenCalledWith('OrderProcess', [
      {
        caseName: 'renamed case',
        input: fixtureCases[0].input,
        expectedOutput: fixtureCases[0].expectedOutput,
      },
      fixtureCases[1],
    ])
    expect(wrapper.find('[data-testid="tests-save-button"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="tests-unsaved-hint"]').exists()).toBe(false)
  })

  it('blocks saving while a JSON editor holds invalid JSON and shows an inline error', async () => {
    const { wrapper, saveTests } = mountPanel()
    await flushPromises()

    await wrapper.find('[data-testid="tests-case-input"]').setValue('{ not json')
    await wrapper.find('[data-testid="tests-case-name"]').setValue('renamed case')

    const error = wrapper.find('[data-testid="tests-case-input-error"]')
    expect(error.exists()).toBe(true)
    expect(error.text()).toContain('Invalid JSON')
    expect(wrapper.find('[data-testid="tests-save-button"]').attributes('disabled')).toBeDefined()

    await wrapper.find('[data-testid="tests-save-button"]').trigger('click')
    expect(saveTests).not.toHaveBeenCalled()
  })

  it('shows an inline error for invalid expected-output JSON', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    await wrapper.find('[data-testid="tests-case-expected"]').setValue('nope')

    expect(wrapper.find('[data-testid="tests-case-expected-error"]').text()).toContain(
      'Invalid JSON',
    )
  })

  it('runs all cases and renders the report with per-status badges', async () => {
    const { wrapper, runTests } = mountPanel()
    await flushPromises()

    await wrapper.find('[data-testid="tests-run-all"]').trigger('click')
    await flushPromises()

    expect(runTests).toHaveBeenCalledWith('OrderProcess', undefined)
    expect(wrapper.find('[data-testid="tests-report"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tests-report-summary"]').text()).toBe(
      '1 passed · 1 failed · 1 errored (of 3)',
    )

    const badges = wrapper.findAll('[data-testid="tests-result-badge"]')
    expect(badges).toHaveLength(3)
    expect(badges[0].classes()).toContain('bg-green-100')
    expect(badges[0].text()).toBe('PASS')
    expect(badges[1].classes()).toContain('bg-yellow-100')
    expect(badges[1].text()).toBe('FAIL')
    expect(badges[2].classes()).toContain('bg-red-100')
    expect(badges[2].text()).toBe('ERROR')

    const durations = wrapper.findAll('[data-testid="tests-result-duration"]')
    expect(durations[0].text()).toContain('12 ms')
    expect(durations[2].text()).toContain('56 ms')
  })

  it('expands a failing case into an actual-vs-expected diff', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    await wrapper.find('[data-testid="tests-run-all"]').trigger('click')
    await flushPromises()

    await wrapper.findAll('[data-testid="tests-result-toggle"]')[1].trigger('click')

    const diff = wrapper.find('[data-testid="tests-result-diff"]')
    expect(diff.exists()).toBe(true)
    expect(diff.text()).toContain('"result": 3')
    expect(diff.text()).toContain('"result": 4')
    expect(diff.find('[data-testid="preview-diff-line"]').exists()).toBe(true)
  })

  it('shows diagnostics instead of a diff for an errored case', async () => {
    const { wrapper } = mountPanel()
    await flushPromises()

    await wrapper.find('[data-testid="tests-run-all"]').trigger('click')
    await flushPromises()

    await wrapper.findAll('[data-testid="tests-result-toggle"]')[2].trigger('click')

    const diagnostics = wrapper.find('[data-testid="tests-result-diagnostics"]')
    expect(diagnostics.exists()).toBe(true)
    expect(diagnostics.text()).toContain('helper exploded')
    expect(wrapper.find('[data-testid="tests-result-diff"]').exists()).toBe(false)
  })

  it('run-selected forwards only the checked case names', async () => {
    const { wrapper, runTests } = mountPanel()
    await flushPromises()

    const checkboxes = wrapper.findAll('[data-testid="tests-case-select"]')
    await checkboxes[1].setValue(true)

    await wrapper.find('[data-testid="tests-run-selected"]').trigger('click')
    await flushPromises()

    expect(runTests).toHaveBeenCalledWith('OrderProcess', ['edge case'])
  })

  it('run-selected stays disabled until a case is checked', async () => {
    const { wrapper, runTests } = mountPanel()
    await flushPromises()

    const button = wrapper.find('[data-testid="tests-run-selected"]')
    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')
    expect(runTests).not.toHaveBeenCalled()
  })

  it('disables run buttons and shows a spinner while a run is in flight', async () => {
    let resolveRun: ((report: DefinitionTestRunReport) => void) | null = null
    const runTests = vi.fn(
      () => new Promise<DefinitionTestRunReport>((resolve) => (resolveRun = resolve)),
    )
    const { wrapper } = mountPanel({ runTests })
    await flushPromises()

    await wrapper.find('[data-testid="tests-run-all"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="tests-running"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tests-run-all"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="tests-run-selected"]').attributes('disabled')).toBeDefined()

    // TS narrows `resolveRun` to null since the assignment happens inside the
    // promise executor — cast back to the callable type.
    ;(resolveRun as unknown as (report: DefinitionTestRunReport) => void)(makeReport())
    await flushPromises()

    expect(wrapper.find('[data-testid="tests-running"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="tests-run-all"]').attributes('disabled')).toBeUndefined()
  })

  it('shows the empty state when no test cases are stored', async () => {
    const { wrapper } = mountPanel({ fetchTests: vi.fn().mockResolvedValue([]) })
    await flushPromises()

    expect(wrapper.find('[data-testid="tests-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tests-case-row"]').exists()).toBe(false)
  })

  it('shows the error state with a retry that reloads', async () => {
    const fetchTests = vi.fn().mockRejectedValue(new Error('backend down'))
    const { wrapper } = mountPanel({ fetchTests })
    await flushPromises()

    const errorState = wrapper.find('[data-testid="tests-error"]')
    expect(errorState.exists()).toBe(true)
    expect(errorState.text()).toContain('backend down')

    fetchTests.mockResolvedValueOnce(fixtureCases)
    await errorState.find('[data-testid="error-banner"]').find('button').trigger('click')
    await flushPromises()

    expect(fetchTests).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="tests-case-row"]').exists()).toBe(true)
  })

  it('surfaces a run failure as a run error message', async () => {
    const { wrapper } = mountPanel({
      runTests: vi.fn().mockRejectedValue(new Error('run blew up')),
    })
    await flushPromises()

    await wrapper.find('[data-testid="tests-run-all"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="tests-run-error"]').text()).toContain('run blew up')
    expect(wrapper.find('[data-testid="tests-report"]').exists()).toBe(false)
  })
})
