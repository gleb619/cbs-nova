import { defineComponent, h } from 'vue'

export const NuxtLink = defineComponent({
  props: ['to'],
  setup(props) {
    return () => h('a', { href: props.to as string }, 'link')
  },
})
