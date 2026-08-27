import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { h, nextTick } from 'vue'
import ConstructExplorer from '../../components/dsl/ConstructExplorer.vue'
import type { DslConstruct } from '../../types/dsl'

const constructs: DslConstruct[] = [
  { name: 'CreateOrder', type: 'Process', status: 'Draft' },
  { name: 'CancelOrder', type: 'Transaction', status: 'Published' },
]

function mountContainer(props: {
  constructs: DslConstruct[]
  selectedName: string | null
  collapsed?: boolean
  loading?: boolean
}) {
  return mount(ConstructExplorer, {
    props,
    slots: {
      default: ({
        constructs,
        selectedName,
        onSelect,
      }: {
        constructs: DslConstruct[]
        selectedName: string | null
        onSelect: (name: string) => void
      }) =>
        h(
          'ul',
          constructs.map((c) =>
            h(
              'li',
              { key: c.name },
              h(
                'button',
                {
                  type: 'button',
                  class: selectedName === c.name ? 'selected' : '',
                  onClick: () => onSelect(c.name),
                },
                c.name,
              ),
            ),
          ),
        ),
    },
  })
}

describe('ConstructExplorer', () => {
  it('renders the header and search input in expanded state', () => {
    const wrapper = mountContainer({ constructs, selectedName: null })

    expect(wrapper.get('h2').text()).toBe('Constructs')
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
  })

  it('lists the provided constructs via the default slot', () => {
    const wrapper = mountContainer({ constructs, selectedName: null })

    const items = wrapper.findAll('li button')
    expect(items.length).toBe(2)
    expect(items[0].text()).toBe('CreateOrder')
    expect(items[1].text()).toBe('CancelOrder')
  })

  it('passes selectedName to the slot', () => {
    const wrapper = mountContainer({ constructs, selectedName: 'CancelOrder' })

    expect(wrapper.find('button.selected').exists()).toBe(true)
    expect(wrapper.find('button.selected').text()).toBe('CancelOrder')
  })

  it('filters constructs based on search input', async () => {
    const wrapper = mountContainer({ constructs, selectedName: null })

    await wrapper.find('input[type="text"]').setValue('create')
    await nextTick()

    const items = wrapper.findAll('li button')
    expect(items.length).toBe(1)
    expect(items[0].text()).toBe('CreateOrder')
  })

  it('emits select when a construct button is clicked', async () => {
    const wrapper = mountContainer({ constructs, selectedName: null })

    const item = wrapper.findAll('li button').find((b) => b.text() === 'CreateOrder')!
    await item.trigger('click')

    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')?.[0]).toEqual(['CreateOrder'])
  })

  it('collapses into the expand rail when collapsed is true and emits updates on toggle', async () => {
    const wrapper = mountContainer({ constructs, selectedName: null, collapsed: true })

    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="explorer-search"]').element?.style.display).toBe('none')
    expect(wrapper.get('[aria-label="Expand constructs"]').exists()).toBe(true)

    await wrapper.get('[aria-label="Expand constructs"]').trigger('click')
    expect(wrapper.emitted('update:collapsed')).toBeTruthy()
    expect(wrapper.emitted('update:collapsed')?.at(-1)).toEqual([false])
  })

  it('emits an update when collapsing an expanded panel', async () => {
    const wrapper = mountContainer({ constructs, selectedName: null })

    await wrapper.get('[aria-label="Collapse constructs"]').trigger('click')

    expect(wrapper.emitted('update:collapsed')).toBeTruthy()
    expect(wrapper.emitted('update:collapsed')?.at(-1)).toEqual([true])
  })

  it('shows a skeleton loader while loading', () => {
    const wrapper = mountContainer({ constructs, selectedName: null, loading: true })

    expect(wrapper.find('[data-testid="construct-list-skeleton"]').exists()).toBe(true)
  })
})
