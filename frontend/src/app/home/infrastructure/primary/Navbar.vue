<template>
  <header class="navbar">
    <div class="navbar__left">
      <button
        class="navbar__collapse"
        @click="$emit('toggle-sidebar')"
        title="Toggle sidebar"
      >
        &#9776;
      </button>
      <div class="navbar__breadcrumb">
        <!-- TODO: make it a link to /dashboard -->
        <span class="navbar__breadcrumb--item">Home</span>
        <span
          v-if="currentRouteName"
          class="navbar__breadcrumb--sep"
        >/</span>
        <span
          v-if="currentRouteName"
          class="navbar__breadcrumb--item navbar__breadcrumb--active"
        >{{ currentRouteName }}</span>
      </div>
    </div>
    <div class="navbar__right">
      <div class="navbar__search">
        <input
          type="text"
          placeholder="Search..."
          class="navbar__search-input"
        />
      </div>
      <button class="navbar__icon-btn" title="Notifications">&#128276;</button>
      <div class="navbar__avatar" title="User profile">&#128100;</div>
    </div>
  </header>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'Navbar',
  props: {
    currentRouteName: {
      type: String,
      default: null,
    },
  },
  emits: ['toggle-sidebar'],
});
</script>

<style scoped>
.navbar {
  height: var(--navbar-height);
  min-height: var(--navbar-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--color-navbar);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: var(--shadow-sm);
  z-index: 30;
  gap: 12px;
}

.navbar__left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.navbar__collapse {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #475569;
  font-size: 18px;
  cursor: pointer;
  transition: background var(--transition), color var(--transition);
}

.navbar__collapse:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.navbar__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #64748b;
}

.navbar__breadcrumb--active {
  color: var(--color-accent);
  font-weight: 600;
}

.navbar__right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.navbar__search {
  position: relative;
}

.navbar__search-input {
  padding: 7px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  font-size: 13px;
  color: #334155;
  width: 200px;
  transition: border-color var(--transition), box-shadow var(--transition), background var(--transition);
  outline: none;
}

.navbar__search-input:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
  background: #fff;
}

.navbar__icon-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  font-size: 18px;
  cursor: pointer;
  transition: background var(--transition), color var(--transition);
}

.navbar__icon-btn:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.navbar__avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #a78bfa);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
  transition: box-shadow var(--transition), transform var(--transition);
}

.navbar__avatar:hover {
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.45);
  transform: scale(1.05);
}

@media (max-width: 768px) {
  .navbar__search-input {
    width: 140px;
  }
}
</style>
