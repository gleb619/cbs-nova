import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PreviewTab from '../dsl/PreviewTab.vue'

function mountTab(props: Record<string, unknown> = {}) {
  return mount(PreviewTab, {
    props: { name: 'demo', ...props },
    global: {
      stubs: {
        ResultTab: {
          template: '<div data-testid="runner-result-tab">{{ result !== undefined ? JSON.stringify(result) : "No result yet." }}</div>',
          props: ['result'],
        },
      },
    },
  })
}

describe('PreviewTab', () => {
  beforeEach(() => {
    vi.stubGlobal('$fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders both input and result panels', () => {
    const wrapper = mountTab()
    expect(wrapper.text()).toContain('Input')
    expect(wrapper.text()).toContain('Result · preview')
    expect(wrapper.text()).toContain('idle')
  })

  it('disables Run when JSON is invalid', async () => {
    const wrapper = mountTab()
    const textarea = wrapper.find('textarea')
    await textarea.setValue('{ bad json')
    const runBtn = wrapper.findAll('button').find((b) => b.text() === 'Run')!
    expect(runBtn.attributes('disabled')).toBeDefined()
  })

  it('calls $fetch and shows done on success', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ result: { ok: true } })
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountTab()
    await wrapper.find('textarea').setValue('{"a":1}')
    await wrapper.findAll('button').find((b) => b.text() === 'Run')!.trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/dsl/preview/demo',
      expect.objectContaining({ method: 'POST', body: { body: { a: 1 } } }),
    )
    expect(wrapper.text()).toContain('done')
    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(true)
  })

  it('surfaces backend errors on failure', async () => {
    const fetchMock = vi.fn().mockRejectedValue({
      data: { errors: [{ message: 'preview failed' }] },
      statusMessage: 'Unprocessable Entity',
    })
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountTab()
    await wrapper.find('textarea').setValue('{}')
    await wrapper.findAll('button').find((b) => b.text() === 'Run')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('preview failed')
    expect(wrapper.text()).toContain('failed')
    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(false)
  })

  it('uses the explain endpoint when configured', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ result: { ok: true } })
    vi.stubGlobal('$fetch', fetchMock)

    const wrapper = mountTab({ endpoint: 'explain' })
    await wrapper.find('textarea').setValue('{}')
    await wrapper.findAll('button').find((b) => b.text() === 'Run')!.trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/dsl/explain/demo',
      expect.any(Object),
    )
    expect(wrapper.text()).toContain('Result · explain')
  })
})
