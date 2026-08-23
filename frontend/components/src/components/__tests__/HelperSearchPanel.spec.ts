import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { ObjectSearchResult } from '../../composables/useHelperSearch'
import HelperSearchPanel from '../dsl/HelperSearchPanel.vue'

// The panel its content into <body> via <Teleport>. Stubbing the Teleport render
// lets us drive form interactions and emitted events through the wrapper itself,
// which the sibling Teleport-based spec drives solely via document queries.
const mountPanel = (props: Record<string, unknown>) =>
  mount(HelperSearchPanel, { props, global: { stubs: { teleport: true } } })

const results: ObjectSearchResult[] = [
  { name: 'ParseDate', type: 'helper', description: 'Parses a date string', inputType: 'String', outputType: 'Date' },
  { name: 'Notify', type: 'process', description: '', inputType: 'Order', outputType: 'Boolean' },
]

describe('HelperSearchPanel', () => {
  let wrapper: ReturnType<typeof mountPanel>

  afterEach(() => {
    wrapper?.unmount()
  })

  it('renders nothing while the drawer is closed', () => {
    wrapper = mountPanel({ results: [], open: false, isLoading: false })

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('opens a dialog with the search controls when open is true', () => {
    wrapper = mountPanel({ results: [], open: true, isLoading: false })

    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.attributes('aria-label')).toBe('Object search')

    expect(wrapper.find('input[placeholder="Name"]').exists()).toBe(true)
    expect(wrapper.find('input[placeholder="Description"]').exists()).toBe(true)
    expect(wrapper.find('select').exists()).toBe(true)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Search')).toBe(true)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Clear')).toBe(true)
  })

  it('shows the empty state when there are no results', () => {
    wrapper = mountPanel({ results: [], open: true, isLoading: false })

    expect(wrapper.text()).toContain('No helpers found.')
    expect(wrapper.find('table').exists()).toBe(false)
  })

  it('renders result rows with name, type, and input/output types', () => {
    wrapper = mountPanel({ results, open: true, isLoading: false })

    const text = wrapper.text()
    expect(text).toContain('ParseDate')
    expect(text).toContain('helper')
    expect(text).toContain('Parses a date string')
    expect(text).toContain('String')
    expect(text).toContain('Date')
    expect(text).toContain('Notify')
    expect(text).toContain('Boolean')
  })

  it('falls back to an em-dash for a missing description and I/O types', () => {
    wrapper = mountPanel({ results, open: true, isLoading: false })

    const notifyRow = wrapper
      .findAll('tr')
      .find((tr) => tr.text().includes('Notify'))!
    expect(notifyRow.text()).toContain('—')
    expect(wrapper.text()).toContain('→')
  })

  it('emits update:name when the name input changes', async () => {
    wrapper = mountPanel({ results: [], open: true, isLoading: false })

    await wrapper.find('input[placeholder="Name"]').setValue('Pay')

    expect(wrapper.emitted('update:name')!.at(-1)).toEqual(['Pay'])
  })

  it('emits update:type when the type select changes', async () => {
    wrapper = mountPanel({ results: [], open: true, isLoading: false })

    await wrapper.find('select').setValue('process')

    expect(wrapper.emitted('update:type')!.at(-1)).toEqual(['process'])
  })

  it('emits update:description when the description input changes', async () => {
    wrapper = mountPanel({ results: [], open: true, isLoading: false })

    await wrapper.find('input[placeholder="Description"]').setValue('helper')

    expect(wrapper.emitted('update:description')!.at(-1)).toEqual(['helper'])
  })

  it('emits search when the Search button is clicked', async () => {
    wrapper = mountPanel({ results: [], open: true, isLoading: false })

    const searchButton = wrapper.findAll('button').find((b) => b.text() === 'Search')!
    await searchButton.trigger('click')
    expect(wrapper.emitted('search')).toBeTruthy()

  })

  it('disables the Search button while loading', () => {
    wrapper = mountPanel({ results: [], open: true, isLoading: true })

    const searchButton = wrapper.findAll('button').find((b) => b.text() === 'Search')!
    expect(searchButton.attributes('disabled')).toBeDefined()
  })

  it('emits clear when the Clear button is clicked', async () => {
    wrapper = mountPanel({ results: [], open: true, isLoading: false })

    const clearButton = wrapper.findAll('button').find((b) => b.text() === 'Clear')!
    await clearButton.trigger('click')

    expect(wrapper.emitted('clear')).toBeTruthy()
  })

  it('renders the error message when one is provided', () => {
    wrapper = mountPanel({ results: [], open: true, isLoading: false, error: 'Failed to search helpers' })

    expect(wrapper.text()).toContain('Failed to search helpers')
  })

  it('renders loading placeholders while results are being fetched', () => {
    wrapper = mountPanel({ results, open: true, isLoading: true })

    expect(wrapper.findAll('.animate-pulse').length).toBeGreaterThan(0)
    expect(wrapper.text()).not.toContain('ParseDate')
  })

  it('closes the drawer via the close button and emits update:open false', async () => {
    wrapper = mountPanel({ results: [], open: true, isLoading: false })

    await wrapper.get('[aria-label="Close object search"]').trigger('click')

    expect(wrapper.emitted('update:open')!.at(-1)).toEqual([false])
  })
})