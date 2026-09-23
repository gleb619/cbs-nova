import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { HelperCatalogEntry, HelpersResponse } from '../../../types/dsl'
import HelperCatalog from '../HelperCatalog.vue'

const allHelpers: HelperCatalogEntry[] = [
  {
    name: 'ParseDate',
    description: 'Parses a date string',
    inputType: 'String',
    outputType: 'Date',
  },
  {
    name: 'SendEmail',
    description: 'Sends an email',
    inputType: 'Order',
    outputType: 'Boolean',
  },
]

function makePaged(
  items: HelperCatalogEntry[],
  total?: number,
  offset = 0,
  limit = 100,
): HelpersResponse {
  return { items, total: total ?? items.length, offset, limit }
}

const mountCatalog = (props: Record<string, unknown>) =>
  mount(HelperCatalog, { props: props as never })

describe('HelperCatalog', () => {
  let wrapper: ReturnType<typeof mountCatalog>

  afterEach(() => {
    wrapper?.unmount()
  })

  it('exposes the root data-testid', async () => {
    wrapper = mountCatalog({
      fetch: vi.fn().mockResolvedValue(makePaged([])),
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="helper-catalog"]').exists()).toBe(true)
  })

  it('renders helper rows with name, I/O types and description', async () => {
    wrapper = mountCatalog({
      fetch: vi.fn().mockResolvedValue(makePaged(allHelpers, allHelpers.length)),
    })
    await flushPromises()

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
  })

  it('fetches with search and mode when filters change', async () => {
    const fetch = vi.fn().mockResolvedValue(makePaged([]))
    wrapper = mountCatalog({ fetch })
    await flushPromises()

    const input = wrapper.find('[data-testid="helper-catalog-search"]')
    await input.setValue('send')
    await input.trigger('input')
    await new Promise((r) => setTimeout(r, 300))
    await flushPromises()

    expect(fetch).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: 'send', mode: 'exact' }),
    )
  })

  it('renders loading skeletons while loading', async () => {
    wrapper = mountCatalog({
      fetch: vi.fn(() => new Promise(() => {})),
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="helper-catalog-loading"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="helper-catalog-item"]')).toHaveLength(0)
  })

  it('renders the error message when fetch fails', async () => {
    wrapper = mountCatalog({
      fetch: vi.fn().mockRejectedValue(new Error('Failed to load helpers')),
    })
    await flushPromises()

    const error = wrapper.find('[data-testid="helper-catalog-error"]')
    expect(error.exists()).toBe(true)
    expect(error.text()).toContain('Failed to load helpers')
  })

  it('renders the empty state when no helpers exist', async () => {
    wrapper = mountCatalog({
      fetch: vi.fn().mockResolvedValue(makePaged([])),
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="helper-catalog-empty"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No helpers registered.')
  })

  it('loads more results when the load-more button is clicked', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(makePaged([allHelpers[0]], 2, 0, 1))
      .mockResolvedValueOnce(makePaged([allHelpers[1]], 2, 1, 1))
    wrapper = mountCatalog({ fetch })
    await flushPromises()

    expect(wrapper.findAll('[data-testid="helper-catalog-item"]')).toHaveLength(1)

    await wrapper.find('[data-testid="helper-catalog-load-more"]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('[data-testid="helper-catalog-item"]')).toHaveLength(2)
    expect(fetch).toHaveBeenLastCalledWith(expect.objectContaining({ offset: 1, limit: 1 }))
  })
})
