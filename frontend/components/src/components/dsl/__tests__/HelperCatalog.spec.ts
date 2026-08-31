import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { HelperCatalogEntry } from '../../types/dsl'
import HelperCatalog from '../HelperCatalog.vue'

const helpers: HelperCatalogEntry[] = [
  {
    name: 'ParseDate',
    description: 'Parses a date string',
    inputType: 'String',
    outputType: 'Date',
    hasSideEffects: false,
  },
  {
    name: 'SendEmail',
    description: 'Sends an email',
    inputType: 'Order',
    outputType: 'Boolean',
    hasSideEffects: true,
    previewBehavior: 'noop',
  },
]

const mountCatalog = (props: Record<string, unknown>) => mount(HelperCatalog, { props })

describe('HelperCatalog', () => {
  let wrapper: ReturnType<typeof mountCatalog>

  afterEach(() => {
    wrapper?.unmount()
  })

  it('exposes the root data-testid', () => {
    wrapper = mountCatalog({ helpers: [] })

    expect(wrapper.find('[data-testid="helper-catalog"]').exists()).toBe(true)
  })

  it('renders helper rows with name, I/O types, description and preview behavior', () => {
    wrapper = mountCatalog({ helpers, loading: false })

    const rows = wrapper.findAll('[data-testid="helper-catalog-item"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('ParseDate')
    expect(rows[0].text()).toContain('String')
    expect(rows[0].text()).toContain('Date')
    expect(rows[0].text()).toContain('Parses a date string')
    expect(rows[1].text()).toContain('SendEmail')
    expect(rows[1].text()).toContain('Order')
    expect(rows[1].text()).toContain('Boolean')
    expect(rows[1].text()).toContain('Sends an email')
    expect(rows[1].text()).toContain('preview: noop')
  })

  it('shows the side-effect badge only for helpers with hasSideEffects', () => {
    wrapper = mountCatalog({ helpers, loading: false })

    const rows = wrapper.findAll('[data-testid="helper-catalog-item"]')
    expect(rows[0].find('[data-testid="helper-catalog-sideeffect"]').exists()).toBe(false)
    const sideEffectBadge = rows[1].find('[data-testid="helper-catalog-sideeffect"]')
    expect(sideEffectBadge.exists()).toBe(true)
    expect(sideEffectBadge.text()).toContain('side effect')
  })

  it('filters the list by name substring', async () => {
    wrapper = mountCatalog({ helpers, loading: false })

    await wrapper.find('[data-testid="helper-catalog-search"]').setValue('send')

    const rows = wrapper.findAll('[data-testid="helper-catalog-item"]')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('SendEmail')
  })

  it('renders loading skeletons while loading', () => {
    wrapper = mountCatalog({ helpers, loading: true })

    expect(wrapper.find('[data-testid="helper-catalog-loading"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="helper-catalog-item"]')).toHaveLength(0)
  })

  it('renders the error message when one is provided', () => {
    wrapper = mountCatalog({ helpers: [], error: 'Failed to load helpers' })

    const error = wrapper.find('[data-testid="helper-catalog-error"]')
    expect(error.exists()).toBe(true)
    expect(error.text()).toContain('Failed to load helpers')
  })

  it('renders the empty state when no helpers exist', () => {
    wrapper = mountCatalog({ helpers: [] })

    expect(wrapper.find('[data-testid="helper-catalog-empty"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No helpers registered.')
  })

  it('renders a no-match state when the search filters everything out', async () => {
    wrapper = mountCatalog({ helpers, loading: false })

    await wrapper.find('[data-testid="helper-catalog-search"]').setValue('xyz')

    expect(wrapper.find('[data-testid="helper-catalog-no-match"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No helpers match.')
  })
})
