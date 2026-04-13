<template>
  <div id="app" class="h-screen bg-neutral-50">
    <div v-if="!ready" class="flex items-center justify-center h-full">
      <div class="text-center">
        <div class="inline-block w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin mb-3" />
        <p class="text-sm text-neutral-500 font-medium">Loading CBS Nova…</p>
      </div>
    </div>
    <AppVue v-else />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import AppVue from './src/app/AppVue.vue';

const ready = ref(false);

onMounted(async () => {
  // Import main.ts to initialize i18next and pinia
  await import('./src/app/main');
  ready.value = true;
});
</script>
