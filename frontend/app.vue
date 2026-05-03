<template>
  <div id="app" class="h-screen bg-neutral-50" />
</template>

<script setup lang="ts">
import { onMounted } from 'vue';

onMounted(async () => {
  // Import main.ts to initialize the Vue SPA (i18next, pinia, router, mount)
  const { app } = await import('./src/app/main');
  app.mount('#app');
});
</script>
