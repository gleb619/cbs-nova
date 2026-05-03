<template>
  <aside
    class="sidebar"
    :class="{ 'sidebar--collapsed': collapsed }"
  >
    <div class="sidebar__header">
      <div class="sidebar__logo">
        <img
          src="/app-icon-64.png"
          alt="CBS Nova"
          class="w-10 h-10 object-contain non-touchable"
          draggable="false"
        />
      </div>
      <transition name="fade">
        <h1
          v-if="!collapsed"
          class="sidebar__title"
        >
          CBS Admin
        </h1>
      </transition>
    </div>

    <nav class="sidebar__nav">
      <!-- Dashboard (always visible, no ABAC filter) -->
      <router-link
        :to="{ name: 'Dashboard' }"
        class="nav-item"
        :class="{ 'nav-item--active': isRouteActive('Dashboard'), 'nav-item--collapsed': collapsed }"
        :title="$t('sidebar.dashboard')"
      >
        <span class="nav-item__icon">📊</span>
        <transition name="fade">
          <span
            v-if="!collapsed"
            class="nav-item__label"
          >{{ $t('sidebar.dashboard') }}</span>
        </transition>
      </router-link>

      <!-- Groups filtered by ABAC -->
      <SidebarGroupVue
        v-for="group in visibleGroups"
        :key="group.key"
        :group="group"
        :collapsed="collapsed"
        :is-open="!!openGroups[group.key]"
        @toggle="toggleGroup(group.key)"
      />
    </nav>
  </aside>
</template>

<script lang="ts">
import SidebarGroupVue from './sidebar/SidebarGroupVue.vue';
import { sidebarGroups } from './sidebar/sidebarConfig';
import { useAbac } from './sidebar/useAbac.ts';
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'Sidebar',
  components: { SidebarGroupVue },
  props: {
    collapsed: {
      type: Boolean,
      required: true,
    },
  },
  data() {
    return {
      openGroups: {} as Record<string, boolean>,
    };
  },
  computed: {
    visibleGroups() {
      //TODO: implement later, with keycloak integration
      //return useAbac().visibleGroups(sidebarGroups);
      return sidebarGroups;
    },
  },
  methods: {
    toggleGroup(key: string) {
      this.openGroups[key] = !this.openGroups[key];
    },
    isRouteActive(name: string): boolean {
      return this.$route.name === name;
    },
  },
});
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  min-width: var(--sidebar-width);
  background: var(--color-sidebar);
  color: var(--color-text-sidebar);
  display: flex;
  flex-direction: column;
  transition: width var(--transition), min-width var(--transition);
  box-shadow: var(--shadow-lg);
  z-index: 40;
  overflow: hidden;
}

.sidebar--collapsed {
  width: var(--sidebar-collapsed-width);
  min-width: var(--sidebar-collapsed-width);
}

.sidebar__header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 16px 16px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  min-height: 60px;
}

.sidebar__logo {
  width: 30px;
  height: 30px;
  min-width: 30px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-light));
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.4);
}

.sidebar__title {
  font-size: 17px;
  font-weight: 700;
  color: #f1f5f9;
  letter-spacing: -0.02em;
  white-space: nowrap;
}

.sidebar__nav {
  flex: 1;
  padding: 12px 8px;
  overflow-y: auto;
  overflow-x: hidden;
}

/* ===== Nav Item ===== */
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: var(--radius);
  color: var(--color-text-sidebar);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: background var(--transition), color var(--transition), box-shadow var(--transition);
  cursor: pointer;
  white-space: nowrap;
}

.nav-item:hover {
  background: var(--color-sidebar-hover);
  color: #e2e8f0;
}

.nav-item--active {
  background: var(--color-sidebar-active);
  color: var(--color-text-sidebar-active);
  box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.2);
}

.nav-item--collapsed {
  justify-content: center;
  padding: 9px 0;
}

.nav-item--collapsed .nav-item__icon {
  min-width: unset;
}

.nav-item__icon {
  font-size: 18px;
  min-width: 20px;
  text-align: center;
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Scrollbar */
.sidebar__nav::-webkit-scrollbar {
  width: 5px;
}
.sidebar__nav::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 10px;
}

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    height: 100vh;
    z-index: 100;
  }
  .sidebar--collapsed {
    width: 0;
    min-width: 0;
  }
}
</style>
