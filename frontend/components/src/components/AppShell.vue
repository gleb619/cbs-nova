<script setup lang="ts">
import { useSidebar } from '../composables/useSidebar'
import AppMainContent from './AppMainContent.vue'
import AppMobileDrawer from './AppMobileDrawer.vue'
import AppNavbar from './AppNavbar.vue'
import AppSidebar from './AppSidebar.vue'
import type { NavItem } from './AppSidebar.vue'
import AppToastContainer from './AppToastContainer.vue'
import AppSidebarRestoreHandle from './sidebar/AppSidebarRestoreHandle.vue'

const props = defineProps<{
  pad?: boolean
  navItems: NavItem[]
  title?: string
  shortTitle?: string
  linkComponent?: string | object
  activeClass?: string
}>()

const { hidden } = useSidebar()
</script>

<template>
  <div data-testid="app-shell" class="flex h-screen overflow-hidden bg-background">
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
        <template #widgets>
          <slot name="widgets" />
        </template>
        <template #trailing>
          <slot name="trailing" />
        </template>
      </AppNavbar>
      <AppMainContent :pad="props.pad">
        <slot />
      </AppMainContent>
      <slot name="footer" />
    </div>
    <!-- Tiny floating tab appears only when the rail is dismissed. -->
    <AppSidebarRestoreHandle v-if="hidden" />
    <!-- Global toast queue — teleports to <body> so it floats above every layout. -->
    <AppToastContainer />
  </div>
</template>
