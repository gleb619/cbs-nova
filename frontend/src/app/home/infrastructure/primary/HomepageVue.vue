<script lang="ts" setup>
import { ref, computed } from 'vue';
import { useRoute } from 'vue-router';
import Navbar from './Navbar.vue';
import Sidebar from './Sidebar.vue';

const route = useRoute();
const sidebarCollapsed = ref(false);

const currentRouteName = (): string | null => {
  if (route.name === 'Settings') return 'Settings';
  if (route.name === 'Homepage') return 'Dashboard';
  return null;
};
</script>

<template>
  <div class="golden-layout">
    <Sidebar :collapsed="sidebarCollapsed" />

    <!-- Main wrapper -->
    <div class="main-wrapper">
      <Navbar
        :current-route-name="currentRouteName()"
        @toggle-sidebar="sidebarCollapsed = !sidebarCollapsed"
      />

      <!-- Main content area -->
      <main class="main-content">
        <router-view :key="route.fullPath" />
      </main>
    </div>
  </div>
</template>

<style scoped>
/* ===== Layout ===== */
.golden-layout {
  display: flex;
  height: calc(100vh - var(--navbar-height));
  font-family: var(--font-sans);
  background: var(--color-bg);
  overflow: hidden;
}

/* ===== Main wrapper ===== */
.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* ===== Main content ===== */
.main-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background: var(--color-bg);
}

/* ===== Scrollbar ===== */
.main-content::-webkit-scrollbar {
  width: 5px;
}
.main-content::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 10px;
}
</style>
