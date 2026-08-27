<script setup lang="ts">
import { useSidebar } from '../composables/useSidebar'
import type { NavItemProps } from './AppNavItem.vue'
import AppNavItem from './AppNavItem.vue'

export interface NavItem extends Omit<NavItemProps, 'isActive' | 'activeClass' | 'linkComponent'> {
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
    activeClass: 'bg-primary-500 text-white',
  },
)

const { collapsed } = useSidebar()
</script>

<template>
  <aside
    data-testid="app-sidebar"
    :class="[
      'hidden md:flex flex-col bg-neutral-800 text-neutral-50 transition-all duration-200 shrink-0 border-r border-neutral-700',
      collapsed ? 'w-16' : 'w-60',
    ]"
  >
    <div class="flex items-center justify-center h-16 border-b border-neutral-700 px-4">
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
          :class="[
            'flex items-center justify-center w-10 h-10 mx-auto rounded-lg text-neutral-100 hover:bg-primary-600 hover:text-white transition-colors',
            item.isActive ? props.activeClass : '',
          ]"
          :active-class="props.activeClass"
          :aria-current="item.isActive ? 'page' : undefined"
        >
          <span v-if="item.icon" class="text-lg" aria-hidden="true">{{ item.icon }}</span>
          <span v-else class="text-xs">{{ item.label.charAt(0) }}</span>
        </component>
      </template>
    </nav>
  </aside>
</template>
