<template>
  <aside
    class="sidebar"
    :class="{ 'sidebar--collapsed': collapsed }"
  >
    <div class="sidebar__header">
      <div class="sidebar__logo">
        <img src="/app-icon-64.png" alt="CBS Nova" class="w-10 h-10 object-contain non-touchable" draggable="false" />
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
      <!-- Dashboard -->
      <router-link
        :to="{ name: 'Homepage' }"
        class="nav-item"
        :class="{ 'nav-item--active': isRouteActive('Homepage') }"
      >
        <span class="nav-item__icon">&#9632;</span>
        <transition name="fade">
          <span
            v-if="!collapsed"
            class="nav-item__label"
          >Dashboard</span>
        </transition>
      </router-link>

      <!-- Finance Group -->
      <div class="nav-group">
        <button
          class="nav-group__toggle"
          @click="toggleGroup('finance')"
        >
          <span class="nav-group__icon">&#128176;</span>
          <transition name="fade">
            <span
              v-if="!collapsed"
              class="nav-group__title"
            >Finance</span>
          </transition>
          <span
            v-if="!collapsed"
            class="nav-group__arrow"
            :class="{ 'nav-group__arrow--open': openGroups.finance }"
          >&#9662;</span>
        </button>
        <transition name="slide">
          <div
            v-if="openGroups.finance && !collapsed"
            class="nav-group__items"
          >
            <a class="nav-sub-item"><span class="nav-sub-item__icon">&#128179;</span>Loans</a>
            <a class="nav-sub-item"><span class="nav-sub-item__icon">&#128171;</span>Currencies</a>
            <a class="nav-sub-item"><span class="nav-sub-item__icon">&#128203;</span>Accounts</a>
          </div>
        </transition>
      </div>

      <!-- Operations Group -->
      <div class="nav-group">
        <button
          class="nav-group__toggle"
          @click="toggleGroup('operations')"
        >
          <span class="nav-group__icon">&#127970;</span>
          <transition name="fade">
            <span
              v-if="!collapsed"
              class="nav-group__title"
            >Operations</span>
          </transition>
          <span
            v-if="!collapsed"
            class="nav-group__arrow"
            :class="{ 'nav-group__arrow--open': openGroups.operations }"
          >&#9662;</span>
        </button>
        <transition name="slide">
          <div
            v-if="openGroups.operations && !collapsed"
            class="nav-group__items"
          >
            <a class="nav-sub-item"><span class="nav-sub-item__icon">&#127962;</span>Branches</a>
            <a class="nav-sub-item"><span class="nav-sub-item__icon">&#128197;</span>Calendar</a>
            <a class="nav-sub-item"><span class="nav-sub-item__icon">&#128218;</span>Dictionaries</a>
          </div>
        </transition>
      </div>

      <!-- Orchestration Group -->
      <div class="nav-group">
        <button
          class="nav-group__toggle"
          @click="toggleGroup('orchestration')"
        >
          <span class="nav-group__icon">&#9881;&#65039;</span>
          <transition name="fade">
            <span
              v-if="!collapsed"
              class="nav-group__title"
            >Orchestration</span>
          </transition>
          <span
            v-if="!collapsed"
            class="nav-group__arrow"
            :class="{ 'nav-group__arrow--open': openGroups.orchestration }"
          >&#9662;</span>
        </button>
        <transition name="slide">
          <div
            v-if="openGroups.orchestration && !collapsed"
            class="nav-group__items"
          >
            <a class="nav-sub-item"><span class="nav-sub-item__icon">&#9881;</span>Executions</a>
            <a class="nav-sub-item"><span class="nav-sub-item__icon">&#128221;</span>Event Log</a>
            <a class="nav-sub-item"><span class="nav-sub-item__icon">&#128279;</span>DSL Rules &#8599;</a>
            <a class="nav-sub-item"><span class="nav-sub-item__icon">&#128279;</span>Temporal UI &#8599;</a>
          </div>
        </transition>
      </div>

      <!-- System Group -->
      <div class="nav-group">
        <button
          class="nav-group__toggle"
          @click="toggleGroup('system')"
        >
          <span class="nav-group__icon">&#9881;</span>
          <transition name="fade">
            <span
              v-if="!collapsed"
              class="nav-group__title"
            >System</span>
          </transition>
          <span
            v-if="!collapsed"
            class="nav-group__arrow"
            :class="{ 'nav-group__arrow--open': openGroups.system }"
          >&#9662;</span>
        </button>
        <transition name="slide">
          <div
            v-if="openGroups.system && !collapsed"
            class="nav-group__items"
          >
            <router-link
              :to="{ name: 'Settings' }"
              class="nav-sub-item"
              :class="{ 'nav-sub-item--active': isRouteActive('Settings') }"
            >
              <span class="nav-sub-item__icon">&#9881;</span>Settings
            </router-link>
            <a class="nav-sub-item"><span class="nav-sub-item__icon">&#128218;</span>Dictionaries</a>
            <a class="nav-sub-item"><span class="nav-sub-item__icon">&#128101;</span>Users</a>
          </div>
        </transition>
      </div>
    </nav>
  </aside>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'Sidebar',
  props: {
    collapsed: {
      type: Boolean,
      required: true,
    },
  },
  data() {
    return {
      openGroups: {
        finance: false,
        operations: false,
        orchestration: false,
        system: false,
      } as Record<string, boolean>,
    };
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

.nav-item__icon {
  font-size: 18px;
  min-width: 20px;
  text-align: center;
}

/* ===== Nav Group ===== */
.nav-group {
  margin-top: 4px;
}

.nav-group__toggle {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 12px;
  border: none;
  border-radius: var(--radius);
  background: transparent;
  color: var(--color-text-sidebar);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--transition), color var(--transition);
  white-space: nowrap;
}

.nav-group__toggle:hover {
  background: var(--color-sidebar-hover);
  color: #e2e8f0;
}

.nav-group__icon {
  font-size: 16px;
  min-width: 20px;
  text-align: center;
}

.nav-group__title {
  flex: 1;
  text-align: left;
}

.nav-group__arrow {
  font-size: 10px;
  transition: transform var(--transition);
  opacity: 0.5;
}

.nav-group__arrow--open {
  transform: rotate(180deg);
}

.nav-group__items {
  padding: 4px 0 4px 20px;
}

/* ===== Nav Sub Item ===== */
.nav-sub-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  border-radius: 8px;
  color: rgba(203, 213, 225, 0.75);
  text-decoration: none;
  font-size: 13px;
  font-weight: 400;
  transition: background var(--transition), color var(--transition);
  cursor: pointer;
  white-space: nowrap;
}

.nav-sub-item:hover {
  background: var(--color-sidebar-hover);
  color: #e2e8f0;
}

.nav-sub-item--active {
  background: var(--color-sidebar-active);
  color: var(--color-text-sidebar-active);
}

.nav-sub-item__icon {
  font-size: 14px;
  min-width: 18px;
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

.slide-enter-active,
.slide-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}
.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}
.slide-enter-to,
.slide-leave-from {
  opacity: 1;
  max-height: 200px;
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
