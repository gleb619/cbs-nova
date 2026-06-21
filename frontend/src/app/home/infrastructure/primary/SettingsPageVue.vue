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
import SettingListVue from '@cbs/admin-plugin/composables/setting/SettingListVue.vue';
import { useSetting } from '@cbs/admin-plugin/composables/setting/useSetting';
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'SettingsPageVue',
  components: { SettingListVue },
  setup() {
    const { settings, loading, error, load } = useSetting();
    load();
    return { settings, loading, error };
  },
});
</script>