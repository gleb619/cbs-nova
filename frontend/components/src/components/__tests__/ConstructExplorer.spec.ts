import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import type { DslConstruct } from '../../types/dsl'
import ConstructExplorer from '../dsl/ConstructExplorer.vue'

const constructs: DslConstruct[] = [
  { name: 'CreateOrder', type: 'Process', status: 'Valid' },
  { name: 'CreditWallet', type: 'Transaction', status: 'Invalid' },
  { name: 'GetOrder', type: 'Function', status: 'Published' },
  { name: 'SendEmail', type: 'Helper', status: 'Draft' },
]

const SlotProbe = defineComponent({
  props: {
    constructs: { type: Array, required: true },
    selectedName: { type: String, default: null },
    onSelect: { type: Function, required: false },
  },
  template: `
    <div>
      <span data-testid="names">{{ constructs.map((c) => c.name).join(',') }}</span>
      <button
        v-for="c in constructs"
        :key="c.name"
        @click="onSelect(c.name)"
      >
        {{ c.name }}
      </button>
    </div>
  `,
})

function mountContainer(props: Record<string, unknown>) {
  return mount(ConstructExplorer, {
    props,
    slots: {
      default: (scope: {
        constructs: DslConstruct[]
        selectedName: string | null
        onSelect?: (name: string) => void
      }) => h(SlotProbe, scope),
    },
  })
}

describe('ConstructExplorer', () => {
  it('renders title, collapse button, and search input', () => {
    const wrapper = mountContainer({ constructs: [], selectedName: null })

    expect(wrapper.text()).toContain('Constructs')
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    expect(wrapper.get('[aria-label="Collapse constructs"]').exists()).toBe(true)
  })

  it('renders loading placeholders instead of the slot while loading', () => {
    const wrapper = mountContainer({ constructs, selectedName: null, loading: true })

    expect(wrapper.findAll('.animate-pulse').length).toBeGreaterThan(0)
    expect(wrapper.find('[data-testid="names"]').exists()).toBe(false)
  })

  it('filters constructs by search and passes the filtered list to the slot', async () => {
    const wrapper = mountContainer({ constructs, selectedName: null })

    expect(wrapper.get('[data-testid="names"]').text()).toBe(
      'CreateOrder,CreditWallet,GetOrder,SendEmail',
    )

    await wrapper.find('input[type="text"]').setValue('Order')

    expect(wrapper.get('[data-testid="names"]').text()).toBe('CreateOrder,GetOrder')
    expect(wrapper.text()).not.toContain('CreditWallet')
    expect(wrapper.text()).not.toContain('SendEmail')
  })

  it('passes selectedName to the slot', () => {
    const wrapper = mountContainer({ constructs, selectedName: 'GetOrder' })

    expect(wrapper.getComponent(SlotProbe).props('selectedName')).toBe('GetOrder')
  })

  it('forwards slot select through its own select event', async () => {
    const wrapper = mountContainer({ constructs, selectedName: null })

    const item = wrapper.findAll('button').find((b) => b.text() === 'CreateOrder')!
    await item.trigger('click')

    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')![0]).toEqual(['CreateOrder'])
  })

  it('collapses into the expand rail when collapsed is true and emits updates on toggle', async () => {
    const wrapper = mountContainer({ constructs, selectedName: null, collapsed: true })

    expect(wrapper.find('input[type="text"]').exists()).toBe(false)
    expect(wrapper.get('[aria-label="Expand constructs"]').exists()).toBe(true)

    await wrapper.get('[aria-label="Expand constructs"]').trigger('click')
    expect(wrapper.emitted('update:collapsed')).toBeTruthy()
    expect(wrapper.emitted('update:collapsed')!.at(-1)).toEqual([false])
  })

  it('emits an update when collapsing an expanded panel', async () => {
    const wrapper = mountContainer({ constructs, selectedName: null })

    await wrapper.get('[aria-label="Collapse constructs"]').trigger('click')

    expect(wrapper.emitted('update:collapsed')).toBeTruthy()
    expect(wrapper.emitted('update:collapsed')!.at(-1)).toEqual([true])
  })
})
