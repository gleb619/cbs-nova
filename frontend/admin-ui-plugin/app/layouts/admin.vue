<script setup lang="ts">
import { AppShell, AppSidebarToggle } from '@cbs/components'
import { useRoute } from 'nuxt/app'
import { computed } from 'vue'
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
  <AppShell
    :nav-items="navItems"
    :link-component="NuxtLink"
    title="CBS Nova"
    short-title="N"
    active-class="bg-primary-500 text-white"
  >
    <template #toggle>
      <AppSidebarToggle />
    </template>
    <template #brand>
      <span class="text-neutral-800 font-semibold">CBS Nova Admin</span>
    </template>
    <template #trailing>
      <span class="text-sm text-neutral-500">User</span>
    </template>
    <slot />
  </AppShell>
</template>
