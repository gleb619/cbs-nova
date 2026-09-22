import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { ObjectSearchResult } from '../../composables/useHelperSearch'
import ObjectsSearchPanel from '../dsl/ObjectsSearchPanel.vue'

// The panel its content into <body> via <Teleport>. Stubbing the Teleport render
// lets us drive form interactions and emitted events through the wrapper itself,
// which the sibling Teleport-based spec drives solely via document queries.
const mountPanel = (props: Record<string, unknown>) =>
  mount(ObjectsSearchPanel, { props: props as never, global: { stubs: { teleport: true } } })

const results: ObjectSearchResult[] = [
  {
    name: 'ParseDate',
    type: 'helper',
    description: 'Parses a date string',
    inputType: 'String',
    outputType: 'Date',
  },
  { name: 'Notify', type: 'process', description: '', inputType: 'Order', outputType: 'Boolean' },
]

describe('ObjectsSearchPanel', () => {
  let wrapper: ReturnType<typeof mountPanel>

  afterEach(() => {
    wrapper?.unmount()
  })

  it('exposes root data-testid', () => {
    wrapper = mountPanel({ results: [], open: true, isLoading: false })

    expect(wrapper.find('[data-testid="objects-search-panel"]').exists()).toBe(true)
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

    expect(wrapper.text()).toContain('No objects found.')
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

    const notifyRow = wrapper.findAll('tr').find((tr) => tr.text().includes('Notify'))!
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
    wrapper = mountPanel({
      results: [],
      open: true,
      isLoading: false,
      error: 'Failed to search objects',
    })

    expect(wrapper.text()).toContain('Failed to search objects')
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

  it('emits select with the clicked result', async () => {
    wrapper = mountPanel({ results, open: true, isLoading: false })

    await wrapper.get('[data-testid="objects-search-result-row-ParseDate"]').trigger('click')

    expect(wrapper.emitted('select')!.at(-1)).toEqual([results[0]])
  })

  it('emits select when Enter is pressed on a row', async () => {
    wrapper = mountPanel({ results, open: true, isLoading: false })

    await wrapper.get('[data-testid="objects-search-result-row-Notify"]').trigger('keydown.enter')

    expect(wrapper.emitted('select')!.at(-1)).toEqual([results[1]])
  })

  it('keeps the panel open after a select so several helpers can be inserted', async () => {
    wrapper = mountPanel({ results, open: true, isLoading: false })

    await wrapper.get('[data-testid="objects-search-result-row-ParseDate"]').trigger('click')
    await wrapper.get('[data-testid="objects-search-result-row-Notify"]').trigger('click')

    expect(wrapper.emitted('select')).toHaveLength(2)
    expect(wrapper.emitted('update:open')).toBeUndefined()
  })

  it('shows no active detail block until a row becomes active', () => {
    wrapper = mountPanel({ results, open: true, isLoading: false })

    expect(wrapper.find('[data-testid="objects-search-active-detail"]').exists()).toBe(false)
  })

  it('ArrowDown activates the first row and shows its input → output detail', async () => {
    wrapper = mountPanel({ results, open: true, isLoading: false })

    await wrapper.get('[data-testid="objects-search-results"]').trigger('keydown.down')

    const detail = wrapper.get('[data-testid="objects-search-active-detail"]')
    expect(detail.text()).toContain('String → Date')
    expect(detail.text()).toContain('Parses a date string')
  })

  it('ArrowDown then ArrowUp moves the active row back', async () => {
    wrapper = mountPanel({ results, open: true, isLoading: false })

    const container = wrapper.get('[data-testid="objects-search-results"]')
    await container.trigger('keydown.down')
    await container.trigger('keydown.down')
    expect(wrapper.get('[data-testid="objects-search-active-detail"]').text()).toContain(
      'Order → Boolean',
    )

    await container.trigger('keydown.up')
    expect(wrapper.get('[data-testid="objects-search-active-detail"]').text()).toContain(
      'String → Date',
    )
  })

  it('clamps arrow navigation at both ends of the result list', async () => {
    wrapper = mountPanel({ results, open: true, isLoading: false })

    const container = wrapper.get('[data-testid="objects-search-results"]')
    await container.trigger('keydown.down')
    await container.trigger('keydown.up')
    await container.trigger('keydown.up')

    expect(wrapper.get('[data-testid="objects-search-active-detail"]').text()).toContain(
      'String → Date',
    )
  })

  it('falls back to placeholders in the active detail for a result without types', async () => {
    wrapper = mountPanel({
      results: [{ name: 'Bare', type: 'helper', description: '', inputType: '', outputType: '' }],
      open: true,
      isLoading: false,
    })

    await wrapper.get('[data-testid="objects-search-results"]').trigger('keydown.down')

    const detail = wrapper.get('[data-testid="objects-search-active-detail"]')
    expect(detail.text()).toContain('— → —')
    expect(detail.text()).toContain('No description')
  })

  it('marks the active row aria-selected', async () => {
    wrapper = mountPanel({ results, open: true, isLoading: false })

    await wrapper.get('[data-testid="objects-search-results"]').trigger('keydown.down')

    expect(
      wrapper.get('[data-testid="objects-search-result-row-ParseDate"]').attributes('aria-selected'),
    ).toBe('true')
  })

  it('Escape in the results list closes the drawer', async () => {
    wrapper = mountPanel({ results, open: true, isLoading: false })

    await wrapper.get('[data-testid="objects-search-results"]').trigger('keydown.esc')

    expect(wrapper.emitted('update:open')!.at(-1)).toEqual([false])
  })

  it('resets the active row when a new search runs', async () => {
    wrapper = mountPanel({ results, open: true, isLoading: false })

    await wrapper.get('[data-testid="objects-search-results"]').trigger('keydown.down')
    expect(wrapper.find('[data-testid="objects-search-active-detail"]').exists()).toBe(true)

    await wrapper.setProps({ results: [results[1]] })
    await wrapper.get('[data-testid="objects-search-search-button"]').trigger('click')

    expect(wrapper.find('[data-testid="objects-search-active-detail"]').exists()).toBe(false)
  })
})
