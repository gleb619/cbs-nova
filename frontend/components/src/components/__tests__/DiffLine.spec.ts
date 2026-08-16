import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { DiffLineKind } from '../../composables/useDiffLines'
import DiffLine from '../runner/DiffLine.vue'

interface KindCase {
  kind: DiffLineKind
  prefix: string
  classes: string[]
}

const KIND_CASES: KindCase[] = [
  {
    kind: 'lhs-only',
    prefix: '−',
    classes: ['bg-red-50', 'border-red-400', 'text-red-900'],
  },
  {
    kind: 'rhs-only',
    prefix: '+',
    classes: ['bg-green-50', 'border-green-500', 'text-green-900'],
  },
  {
    kind: 'same',
    prefix: ' ',
    classes: ['border-transparent', 'text-gray-700'],
  },
]

function mountLine(props: Record<string, unknown>) {
  return mount(DiffLine, { props })
}

describe('DiffLine', () => {
  it.each(KIND_CASES)('renders $kind with the correct prefix and colour classes', ({
    kind,
    prefix,
    classes,
  }) => {
    const wrapper = mountLine({ kind, text: 'line text' })

    const root = wrapper.find('[data-testid="preview-diff-line"]')
    expect(root.exists()).toBe(true)
    expect(root.attributes('data-kind')).toBe(kind)

    for (const cls of classes) {
      expect(root.classes()).toContain(cls)
    }

    // Template order: lhs#, rhs#, gutter, text. Use the 3rd span directly
    // so we don't depend on whitespace-equality (Vue Test Utils trims text()
    // — a single space becomes '').
    const gutter = wrapper.findAll('span')[2]
    expect(gutter.exists()).toBe(true)
    if (prefix.trim().length > 0) {
      expect(gutter.text()).toBe(prefix)
    } else {
      // 'same' gutter is whitespace-only — text() collapses it.
      expect(gutter.text()).toBe('')
    }

    expect(wrapper.text()).toContain('line text')
  })

  it('renders lhs and rhs line numbers when provided', () => {
    const wrapper = mountLine({
      kind: 'same',
      text: 'aligned',
      lhsLineNumber: 7,
      rhsLineNumber: 12,
    })

    const spans = wrapper.findAll('span')
    const numbers = spans.map((s) => s.text()).filter((t) => /^\d+$/.test(t))
    expect(numbers).toEqual(['7', '12'])
  })

  it('renders only the lhs line number when rhs is null', () => {
    const wrapper = mountLine({
      kind: 'lhs-only',
      text: 'left only',
      lhsLineNumber: 3,
      rhsLineNumber: null,
    })

    const spans = wrapper.findAll('span')
    const numbers = spans.map((s) => s.text())
    expect(numbers).toContain('3')
    // rhs gutter renders empty string when null
    expect(numbers.filter((t) => t === '').length).toBeGreaterThanOrEqual(1)
  })

  it('renders only the rhs line number when lhs is null', () => {
    const wrapper = mountLine({
      kind: 'rhs-only',
      text: 'right only',
      lhsLineNumber: null,
      rhsLineNumber: 9,
    })

    const spans = wrapper.findAll('span')
    const numbers = spans.map((s) => s.text())
    expect(numbers).toContain('9')
    expect(numbers.filter((t) => t === '').length).toBeGreaterThanOrEqual(1)
  })

  it('omits both line number slots when both are absent', () => {
    const wrapper = mountLine({ kind: 'same', text: 'no numbers' })

    const spans = wrapper.findAll('span')
    const numbers = spans.map((s) => s.text())
    expect(numbers.filter((t) => /^\d+$/.test(t))).toEqual([])
    expect(numbers.filter((t) => t === '').length).toBeGreaterThanOrEqual(2)
  })
})
