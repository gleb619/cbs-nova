import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ErrorList from '../ErrorList.vue'

const source = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), '../ErrorList.vue'),
  'utf8',
)

describe('ErrorList', () => {
  it('renders the primitive root testid', () => {
    const wrapper = mount(ErrorList, { props: { items: [] } })
    expect(wrapper.find('[data-testid="error-list"]').exists()).toBe(true)
  })

  it('renders the default empty text when items is undefined', () => {
    const wrapper = mount(ErrorList, { props: { items: undefined } })
    expect(wrapper.text()).toContain('No errors.')
  })

  it('honours a custom emptyText prop when items is an empty array', () => {
    const wrapper = mount(ErrorList, {
      props: { items: [], emptyText: 'All clear.' },
    })
    expect(wrapper.text()).toContain('All clear.')
    expect(wrapper.text()).not.toContain('No errors.')
  })

  it('honours a custom emptyText prop when items is undefined', () => {
    const wrapper = mount(ErrorList, {
      props: { items: undefined, emptyText: 'Nothing to show.' },
    })
    expect(wrapper.text()).toContain('Nothing to show.')
  })

  it('renders each item message and code as a badge by default', () => {
    const wrapper = mount(ErrorList, {
      props: {
        items: [
          { message: 'Boom', code: 'E_1' },
          { message: 'Bang', code: 'E_2' },
        ],
      },
    })
    expect(wrapper.text()).toContain('Boom')
    expect(wrapper.text()).toContain('Bang')
    expect(wrapper.text()).toContain('E_1')
    expect(wrapper.text()).toContain('E_2')
    const badges = wrapper.findAll('span.bg-red-200')
    expect(badges.length).toBeGreaterThanOrEqual(2)
  })

  it('renders code as plain "Code: X" text when codeStyle is text', () => {
    const wrapper = mount(ErrorList, {
      props: {
        items: [{ message: 'Boom', code: 'ERR_001' }],
        codeStyle: 'text',
      },
    })
    expect(wrapper.text()).toContain('Code: ERR_001')
    expect(wrapper.findAll('span.bg-red-200')).toHaveLength(0)
  })

  it('hides the stack trace toggle when showStackTrace is false', () => {
    const wrapper = mount(ErrorList, {
      props: {
        variant: 'executions',
        items: [{ message: 'Oops', code: 'E_3', stackTrace: 'at line 1' }],
        showStackTrace: false,
      },
    })
    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('at line 1')
  })

  it('hides the stack trace toggle when stackTrace is missing even if showStackTrace is true', () => {
    const wrapper = mount(ErrorList, {
      props: {
        variant: 'executions',
        items: [{ message: 'No stack', code: 'E_4' }],
        showStackTrace: true,
      },
    })
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('toggles the stack trace open and closed via the button', async () => {
    const wrapper = mount(ErrorList, {
      props: {
        variant: 'executions',
        items: [{ message: 'Oops', code: 'E_5', stackTrace: 'frame 1\nframe 2' }],
        showStackTrace: true,
      },
    })

    const toggle = wrapper.find('button')
    expect(toggle.exists()).toBe(true)
    expect(toggle.text()).toBe('Show stack trace')
    expect(wrapper.text()).not.toContain('frame 1')

    await toggle.trigger('click')
    expect(wrapper.find('button').text()).toBe('Hide stack trace')
    expect(wrapper.text()).toContain('frame 1')

    await wrapper.find('button').trigger('click')
    expect(wrapper.find('button').text()).toBe('Show stack trace')
    expect(wrapper.text()).not.toContain('frame 1')
  })

  it('does not use the ref<Set> reassignment reactivity hack', () => {
    expect(source).not.toMatch(/expanded\.value\s*=\s*new\s+Set\(/)
    expect(source).toMatch(/reactive</)
  })
})
