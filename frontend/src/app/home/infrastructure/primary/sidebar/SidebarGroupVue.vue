<template>
  <div class="nav-group">
    <button
      class="nav-group__toggle"
      :class="{ 'nav-group__toggle--collapsed': collapsed }"
      :title="$t(group.label)"
      @click="$emit('toggle')"
    >
      <span class="nav-group__icon">{{ iconEmoji }}</span>
      <transition name="fade">
        <span
          v-if="!collapsed"
          class="nav-group__title"
        >{{ $t(group.label) }}</span>
      </transition>
      <span
        v-if="!collapsed"
        class="nav-group__arrow"
        :class="{ 'nav-group__arrow--open': isOpen }"
      >▼</span>
    </button>
    <div
      v-show="isOpen && !collapsed"
      class="nav-group__items"
      :class="isOpen ? 'nav-group__items--open' : 'nav-group__items--closed'"
    >
      <template
        v-for="item in group.items"
        :key="item.key"
      >
        <!-- External link -->
        <a
          v-if="item.externalUrl"
          class="nav-sub-item"
          :href="item.externalUrl"
          target="_blank"
          rel="noopener noreferrer"
          :title="$t(item.label)"
        >
          <span class="nav-sub-item__icon">🔗</span>{{ $t(item.label) }} ↗
        </a>
        <!-- Internal route -->
        <a
          v-else-if="item.routeName"
          class="nav-sub-item"
          :class="{ 'nav-sub-item--active': isRouteActive(item.routeName) }"
          :title="$t(item.label)"
          @click.prevent="navigateTo(item.routeName)"
        >
          <span class="nav-sub-item__icon">{{ itemIcon(item) }}</span>{{ $t(item.label) }}
        </a>
        <!-- Fallback: item without a route (TBD features) -->
        <span
          v-else
          class="nav-sub-item nav-sub-item--disabled"
          :title="$t(item.label) + ' (coming soon)'"
        >
          <span class="nav-sub-item__icon">{{ itemIcon(item) }}</span>{{ $t(item.label) }}
        </span>
      </template>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, type PropType } from 'vue';
import type { SidebarGroup, SidebarItem } from './types';

/** Map group key to a default emoji icon */
const GROUP_ICONS: Record<string, string> = {
  finance: '💰',
  operations: '🏦',
  orchestration: '⚙️',
  system: '⚙',
};

/** Map item key to a default emoji icon */
const ITEM_ICONS: Record<string, string> = {
  loans: '💳',
  currencies: '💫',
  accounts: '📋',
  branches: '🏢',
  calendar: '📅',
  dictionaries: '📚',
  executions: '⚙',
  'event-log': '📝',
  'dsl-rules': '🔗',
  'temporal-ui': '🔗',
  settings: '⚙',
  users: '👥',
};

export default defineComponent({
  name: 'SidebarGroupVue',
  props: {
    group: {
      type: Object as PropType<SidebarGroup>,
      required: true,
    },
    collapsed: {
      type: Boolean,
      required: true,
    },
    isOpen: {
      type: Boolean,
      required: true,
    },
  },
  emits: ['toggle', 'select-item'],
  computed: {
    iconEmoji(): string {
      return GROUP_ICONS[this.group.key] || '⬛';
    },
  },
  methods: {
    itemIcon(item: SidebarItem): string {
      return ITEM_ICONS[item.key] || '⬛';
    },
    isRouteActive(routeName: string | undefined): boolean {
      if (!routeName) return false;
      const currentName = this.$route.name;
      const currentPath = this.$route.path;
      // Exact name match
      if (currentName === routeName) return true;
      // For routes with params (e.g., executions/:id), check if current path starts with the base path
      const targetRoute = this.$router.getRoutes().find(r => r.name === routeName);
      if (targetRoute?.path) {
        const basePath = targetRoute.path.startsWith('/') ? targetRoute.path : `/${targetRoute.path}`;
        const pathBase = basePath.split(':')[0];
        if (pathBase.length > 1 && currentPath.startsWith(pathBase)) return true;
      }
      return false;
    },
    /** Navigate to a route by name, safely catching errors for unregistered routes */
    navigateTo(routeName: string) {
      this.$router.push({ name: routeName }).catch(() => {
        // Route doesn't exist — silently ignore (TBD feature)
      });
    },
  },
});
</script>

<style scoped>
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

.nav-group__toggle--collapsed {
  justify-content: center;
  padding: 9px 0;
}

.nav-group__toggle--collapsed .nav-group__icon {
  min-width: unset;
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
  overflow: hidden;
  max-height: 0;
  opacity: 0;
  padding: 4px 0 4px 20px;
  transition: max-height 0.25s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.2s ease, padding 0.25s ease;
}

.nav-group__items--open {
  max-height: 500px;
  opacity: 1;
}

.nav-group__items--closed {
  padding-top: 0;
  padding-bottom: 0;
}

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

.nav-sub-item--disabled {
  opacity: 0.4;
  cursor: not-allowed;
  pointer-events: none;
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
</style>
