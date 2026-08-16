import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { RunnerMode } from '../../types/runner'
import ModeSwitcher from '../runner/ModeSwitcher.vue'

const EXPECTED_MODES: RunnerMode[] = ['preview', 'run', 'explain']
const EXPECTED_LABELS = ['Preview', 'Run', 'Explain']

function mountSwitcher(modelValue: RunnerMode) {
  return mount(ModeSwitcher, { props: { modelValue } })
}

function findModeButton(wrapper: ReturnType<typeof mountSwitcher>, label: string) {
  return wrapper.findAll('button').find((b) => b.text() === label)
}

describe('ModeSwitcher', () => {
  it('renders three buttons labelled Preview / Run / Explain in a radiogroup', () => {
    const wrapper = mountSwitcher('run')

    const root = wrapper.find('[role="radiogroup"]')
    expect(root.exists()).toBe(true)
    expect(root.attributes('aria-label')).toBe('Runner mode')

    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(3)
    expect(buttons.map((b) => b.text())).toEqual(EXPECTED_LABELS)
  })

  it.each<{ mode: RunnerMode; activeLabel: string }>([
    { mode: 'preview', activeLabel: 'Preview' },
    { mode: 'run', activeLabel: 'Run' },
    { mode: 'explain', activeLabel: 'Explain' },
  ])('marks the active mode with the blue background and white text', ({ mode, activeLabel }) => {
    const wrapper = mountSwitcher(mode)

    for (const label of EXPECTED_LABELS) {
      const button = findModeButton(wrapper, label)
      expect(button, `expected to find a "${label}" button`).toBeDefined()
      const isActive = label === activeLabel
      if (isActive) {
        expect(button?.classes()).toContain('bg-blue-600')
        expect(button?.classes()).toContain('text-white')
        expect(button?.attributes('aria-checked')).toBe('true')
      } else {
        expect(button?.classes()).toContain('bg-white')
        expect(button?.classes()).toContain('text-gray-700')
        expect(button?.attributes('aria-checked')).toBe('false')
      }
    }
  })

  it.each<RunnerMode>(
    EXPECTED_MODES,
  )('emits update:modelValue with %s when its button is clicked', async (mode) => {
    const wrapper = mountSwitcher('run')
    const label = EXPECTED_LABELS[EXPECTED_MODES.indexOf(mode)]
    const button = findModeButton(wrapper, label)
    expect(button).toBeDefined()

    await button?.trigger('click')

    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeDefined()
    expect(emitted).toHaveLength(1)
    expect(emitted?.[0]).toEqual([mode])
  })
})
