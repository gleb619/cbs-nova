<script setup lang="ts">
import AppHeader from '~/components/AppHeader.vue'
</script>

<template>
  <div class="min-h-screen flex flex-col bg-background">
    <AppHeader />
    <main class="flex-1">
      <slot />
    </main>
    <footer class="bg-white border-t border-neutral-200 py-6">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-sm text-neutral-500">
        © CBS Operator Portal — internal use only.
      </div>
    </footer>
  </div>
</template>
