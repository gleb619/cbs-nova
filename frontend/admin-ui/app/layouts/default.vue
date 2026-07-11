<script setup lang="ts">
import { NuxtLink } from '#components'

const route = useRoute()

const navItems = computed(() => [
  { to: '/', label: 'Dashboard', icon: '🏠', isActive: route.path === '/' },
  {
    to: '/dsl-workbench',
    label: 'DSL Workbench',
    icon: '⚙️',
    isActive: route.path === '/dsl-workbench',
  },
  { to: '/runner', label: 'Runner', icon: '▶️', isActive: route.path === '/runner' },
  {
    to: '/executions',
    label: 'Executions',
    icon: '📋',
    isActive: route.path.startsWith('/executions'),
  },
])
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-gray-50">
    <AppSidebar
      :items="navItems"
      :link-component="NuxtLink"
      active-class="bg-gray-800 text-white"
      title="CBS Nova"
      short-title="N"
    />
    <AppMobileDrawer
      :items="navItems"
      :link-component="NuxtLink"
      active-class="bg-gray-800 text-white"
      title="CBS Nova"
    />
    <div class="flex flex-col flex-1 overflow-hidden">
      <AppTopBar />
      <main class="flex-1 overflow-y-auto p-6">
        <slot />
      </main>
    </div>
  </div>
</template>
