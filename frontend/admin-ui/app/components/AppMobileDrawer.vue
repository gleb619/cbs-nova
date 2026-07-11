<script setup lang="ts">
const { mobileOpen, closeMobile } = useSidebar()

const navItems = [
  { to: '/', label: 'Dashboard', icon: '🏠' },
  { to: '/workbench', label: 'DSL Workbench', icon: '⚙️' },
  { to: '/runner', label: 'Runner', icon: '▶️' },
  { to: '/executions', label: 'Executions', icon: '📋' },
]
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="mobileOpen"
        class="fixed inset-0 z-30 bg-black/50 md:hidden"
        aria-hidden="true"
        @click="closeMobile"
      />
    </Transition>
    <Transition name="slide">
      <aside
        v-if="mobileOpen"
        class="fixed top-0 left-0 z-40 h-full w-64 bg-gray-900 text-gray-100 flex flex-col md:hidden"
        role="dialog"
        aria-modal="true"
        aria-label="Navigation menu"
      >
        <div class="flex items-center justify-between h-16 border-b border-gray-700 px-4">
          <span class="text-white font-bold text-lg">CBS Nova</span>
          <button
            type="button"
            class="text-gray-300 hover:text-white p-1 rounded"
            aria-label="Close navigation menu"
            @click="closeMobile"
          >
            ✕
          </button>
        </div>
        <nav class="flex-1 overflow-y-auto p-2 space-y-1">
          <AppNavItem
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            :label="item.label"
            :icon="item.icon"
            @click="closeMobile"
          />
        </nav>
      </aside>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
.slide-enter-active,
.slide-leave-active {
  transition: transform 0.2s;
}
.slide-enter-from,
.slide-leave-to {
  transform: translateX(-100%);
}
</style>
