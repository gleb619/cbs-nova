<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    to: string
    label: string
    icon?: string
    isActive?: boolean
    activeClass?: string
    linkComponent?: 'a' | unknown
  }>(),
  {
    activeClass: 'bg-gray-800 text-white',
    linkComponent: 'a',
  },
)

const baseClasses =
  'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors text-gray-300 hover:bg-gray-800 hover:text-white'
</script>

<template>
  <a
    v-if="linkComponent === 'a'"
    :href="props.to"
    :class="[baseClasses, props.isActive ? props.activeClass : '']"
    :aria-current="props.isActive ? 'page' : undefined"
  >
    <span v-if="props.icon" class="text-lg">{{ props.icon }}</span>
    <span class="truncate">{{ props.label }}</span>
  </a>
  <component
    :is="props.linkComponent"
    v-else
    :to="props.to"
    :class="[baseClasses, props.isActive ? props.activeClass : '']"
    :aria-current="props.isActive ? 'page' : undefined"
  >
    <span v-if="props.icon" class="text-lg">{{ props.icon }}</span>
    <span class="truncate">{{ props.label }}</span>
  </component>
</template>
