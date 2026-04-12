<template>
  <div>
    <div
      v-if="settings.length === 0"
      data-testid="empty-state"
    >
      No settings available.
    </div>
    <div
      v-for="setting in settings"
      :key="setting.id"
      data-testid="setting-row"
    >
      <span>{{ setting.code }}</span>
      <span>{{ setting.value }}</span>
      <span>{{ setting.description }}</span>
    </div>
  </div>
</template>

<script lang="ts">
import type { Setting } from './Setting';

export default {
  name: 'SettingListVue',
  props: {
    settings: {
      type: Array as () => Setting[],
      required: true,
    },
  },
};
</script>
