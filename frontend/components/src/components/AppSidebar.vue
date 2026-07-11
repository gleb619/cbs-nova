<script setup lang="ts">
import { useSidebar } from '../composables/useSidebar'
import AppNavItem from './AppNavItem.vue'

export interface NavItem {
  to: string
  label: string
  icon?: string
  isActive?: boolean
}

const props = withDefaults(
  defineProps<{
    items: NavItem[]
    title?: string
    shortTitle?: string
    linkComponent?: 'a' | unknown
    activeClass?: string
  }>(),
  {
    title: 'CBS Nova',
    shortTitle: 'N',
    linkComponent: 'a',
    activeClass: 'bg-gray-800 text-white',
  },
)

const { collapsed } = useSidebar()
</script>

<template>
  <aside
    :class="[
      'hidden md:flex flex-col bg-gray-900 text-gray-100 transition-all duration-200 shrink-0',
      collapsed ? 'w-16' : 'w-60',
    ]"
  >
    <div class="flex items-center justify-center h-16 border-b border-gray-700 px-4">
      <span v-if="!collapsed" class="text-white font-bold text-lg truncate">{{ props.title }}</span>
      <span v-else class="text-white font-bold">{{ props.shortTitle }}</span>
    </div>
    <nav class="flex-1 overflow-y-auto p-2 space-y-1">
      <template v-for="item in props.items" :key="item.to">
        <AppNavItem
          v-if="!collapsed"
          :to="item.to"
          :label="item.label"
          :icon="item.icon"
          :is-active="item.isActive"
          :active-class="props.activeClass"
          :link-component="props.linkComponent"
        />
        <component
          :is="props.linkComponent"
          v-else
          :to="item.to"
          :href="item.to"
          :class="
            'flex items-center justify-center w-10 h-10 mx-auto rounded-lg text-gray-300 hover:bg-gray-800 hover:text-white transition-colors'
          "
          :active-class="props.activeClass"
        >
          {{ item.icon }}
        </component>
      </template>
    </nav>
  </aside>
</template>
