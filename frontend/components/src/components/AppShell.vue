<script setup lang="ts">
import AppMainContent from './AppMainContent.vue'
import AppMobileDrawer from './AppMobileDrawer.vue'
import AppNavbar from './AppNavbar.vue'
import type { NavItem } from './AppSidebar.vue'
import AppSidebar from './AppSidebar.vue'

const props = defineProps<{
  navItems: NavItem[]
  title?: string
  shortTitle?: string
  linkComponent?: 'a' | unknown
  activeClass?: string
}>()
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-background">
    <AppSidebar
      :items="props.navItems"
      :title="props.title"
      :short-title="props.shortTitle"
      :link-component="props.linkComponent"
      :active-class="props.activeClass"
    />
    <AppMobileDrawer
      :items="props.navItems"
      :title="props.title"
      :link-component="props.linkComponent"
      :active-class="props.activeClass"
    />
    <div class="flex flex-col flex-1 overflow-hidden">
      <AppNavbar>
        <template #toggle>
          <slot name="toggle" />
        </template>
        <template #brand>
          <slot name="brand" />
        </template>
        <template #trailing>
          <slot name="trailing" />
        </template>
      </AppNavbar>
      <AppMainContent>
        <slot />
      </AppMainContent>
    </div>
  </div>
</template>
