<script setup lang="ts">
export interface NavItemProps {
  to: string
  label: string
  icon?: string
  isActive?: boolean
  activeClass?: string
  linkComponent?: 'a' | unknown
}

const props = withDefaults(defineProps<NavItemProps>(), {
  activeClass: 'bg-primary-500 text-white',
  linkComponent: 'a',
})

const baseClasses =
  'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors text-neutral-100 hover:bg-primary-600 hover:text-white'
</script>

<template>
  <a
    v-if="props.linkComponent === 'a'"
    :href="props.to"
    :class="[baseClasses, props.isActive ? props.activeClass : '']"
    :aria-current="props.isActive ? 'page' : undefined"
  >
    <span v-if="props.icon" class="text-lg" aria-hidden="true">{{ props.icon }}</span>
    <span class="truncate">{{ props.label }}</span>
  </a>
  <component
    :is="props.linkComponent"
    v-else
    :to="props.to"
    :class="[baseClasses, props.isActive ? props.activeClass : '']"
    :aria-current="props.isActive ? 'page' : undefined"
  >
    <span v-if="props.icon" class="text-lg" aria-hidden="true">{{ props.icon }}</span>
    <span class="truncate">{{ props.label }}</span>
  </component>
</template>
