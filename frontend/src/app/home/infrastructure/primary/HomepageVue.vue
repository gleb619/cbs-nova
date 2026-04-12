<template>
  <div class="golden-layout">
    <Sidebar :collapsed="sidebarCollapsed" />

    <!-- Main wrapper -->
    <div class="main-wrapper">
      <Navbar
        :current-route-name="currentRouteName"
        @toggle-sidebar="sidebarCollapsed = !sidebarCollapsed"
      />

      <!-- Main content area -->
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import Sidebar from './Sidebar.vue';
import Navbar from './Navbar.vue';

export default defineComponent({
  name: 'HomepageVue',
  components: {
    Sidebar,
    Navbar,
  },
  data() {
    return {
      sidebarCollapsed: false,
    };
  },
  computed: {
    currentRouteName(): string | null {
      const route = this.$route;
      if (route.name === 'Settings') return 'Settings';
      if (route.name === 'Homepage') return 'Dashboard';
      return null;
    },
  },
});
</script>

<style scoped>
/* ===== CSS Variables ===== */
:root,
.golden-layout {
  --sidebar-width: 260px;
  --sidebar-collapsed-width: 64px;
  --navbar-height: 56px;
  --color-bg: #f0f2f5;
  --color-sidebar: linear-gradient(180deg, #0f172a 0%, #1e293b 100%);
  --color-sidebar-hover: rgba(255, 255, 255, 0.08);
  --color-sidebar-active: rgba(99, 102, 241, 0.25);
  --color-text-sidebar: #cbd5e1;
  --color-text-sidebar-active: #e0e7ff;
  --color-navbar: rgba(255, 255, 255, 0.82);
  --color-accent: #6366f1;
  --color-accent-light: #818cf8;
  --shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.08);
  --shadow-md: 0 4px 12px rgba(0, 0, 0, 0.1);
  --shadow-lg: 0 8px 30px rgba(0, 0, 0, 0.12);
  --radius: 10px;
  --font-sans: 'Inter', 'Segoe UI', system-ui, -apple-system, sans-serif;
  --transition: 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

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
