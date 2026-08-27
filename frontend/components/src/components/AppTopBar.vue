<script setup lang="ts">
import { useSidebar } from '../composables/useSidebar'

const { toggle, openMobile } = useSidebar()
</script>

<template>
  <header
    data-testid="app-top-bar"
    class="sticky top-0 z-10 h-16 bg-white border-b border-gray-200 flex items-center px-4 gap-4 shrink-0"
  >
    <button
      type="button"
      data-testid="app-top-bar-toggle"
      class="text-gray-500 hover:text-gray-700 p-1.5 rounded-lg hidden md:flex"
      aria-label="Toggle sidebar"
      @click="toggle"
    >
      ☰
    </button>
    <button
      type="button"
      data-testid="app-top-bar-mobile-menu"
      class="text-gray-500 hover:text-gray-700 p-1.5 rounded-lg md:hidden"
      aria-label="Open navigation menu"
      @click="openMobile"
    >
      ☰
    </button>
    <span class="text-gray-900 font-semibold">CBS Nova Admin</span>
    <div class="ml-auto flex items-center gap-2">
      <span class="text-sm text-gray-500">User</span>
    </div>
  </header>
</template>
