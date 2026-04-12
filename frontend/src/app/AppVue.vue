<script lang="ts">
export default {
  name: 'AppVue',
};
</script>

<template>
  <div class="flex flex-col h-screen">
    <main class="flex-grow overflow-auto">
      <router-view />
    </main>

    <footer class="py-6 px-4 bg-white border-t border-neutral-200">
      <div class="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-center gap-4 text-xs text-neutral-400">
        <div>
          © {{ new Date().getFullYear() }} CBS Nova. All rights reserved.
        </div>
        <div class="flex gap-6">
          <router-link to="/privacy" class="hover:text-neutral-900 transition-colors">Privacy Policy</router-link>
          <router-link to="/terms" class="hover:text-neutral-900 transition-colors">Terms of Service</router-link>
          <router-link to="/help" class="hover:text-neutral-900 transition-colors">Help</router-link>
        </div>
      </div>
    </footer>
  </div>
</template>
