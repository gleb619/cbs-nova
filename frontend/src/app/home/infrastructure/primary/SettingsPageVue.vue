<template>
  <div>
    <div v-if="loading">Loading...</div>
    <div
      v-else-if="error"
      data-testid="error-message"
    >
      {{ error }}
    </div>
    <SettingListVue
      v-else
      :settings="settings"
    />
  </div>
</template>

<script lang="ts">
import type { Setting } from '@cbs/admin-plugin/composables/setting/Setting';
import SettingListVue from '@cbs/admin-plugin/composables/setting/SettingListVue.vue';
import { SETTING_REPOSITORY, inject } from '@cbs/admin-plugin/composables/setting/SettingProvider';

export default {
  name: 'SettingsPageVue',
  components: { SettingListVue },
  data() {
    return {
      loading: false,
      settings: [] as Setting[],
      error: null as string | null,
    };
  },
  async mounted() {
    this.loading = true;
    try {
      const repository = inject(SETTING_REPOSITORY);
      this.settings = await repository.findAll();
    } catch (e: unknown) {
      this.error = e instanceof Error ? e.message : 'An error occurred while fetching settings.';
    } finally {
      this.loading = false;
    }
  },
};
</script>
