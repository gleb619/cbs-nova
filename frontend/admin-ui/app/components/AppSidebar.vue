<script setup lang="ts">
const { collapsed } = useSidebar()

const navItems = [
  { to: '/', label: 'Dashboard', icon: '🏠' },
  { to: '/workbench', label: 'DSL Workbench', icon: '⚙️' },
  { to: '/runner', label: 'Runner', icon: '▶️' },
  { to: '/executions', label: 'Executions', icon: '📋' },
]
</script>

<template>
  <aside
    :class="['hidden md:flex flex-col bg-gray-900 text-gray-100 transition-all duration-200 shrink-0', collapsed ? 'w-16' : 'w-60']"
  >
    <div class="flex items-center justify-center h-16 border-b border-gray-700 px-4">
      <span v-if="!collapsed" class="text-white font-bold text-lg truncate">CBS Nova</span>
      <span v-else class="text-white font-bold">N</span>
    </div>
    <nav class="flex-1 overflow-y-auto p-2 space-y-1">
      <template v-for="item in navItems" :key="item.to">
        <AppNavItem v-if="!collapsed" :to="item.to" :label="item.label" :icon="item.icon" />
        <NuxtLink
          v-else
          :to="item.to"
          class="flex items-center justify-center w-10 h-10 mx-auto rounded-lg text-gray-300 hover:bg-gray-800 hover:text-white transition-colors"
          active-class="bg-gray-800 text-white"
          :title="item.label"
        >
          {{ item.icon }}
        </NuxtLink>
      </template>
    </nav>
  </aside>
</template>