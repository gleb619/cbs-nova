<script setup lang="ts">
const runtimeConfig = useRuntimeConfig()
const portalName = runtimeConfig.public.portalName || 'CBS Operator Portal'
</script>

<template>
  <header class="bg-white border-b border-neutral-200">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <span class="text-xl font-bold text-primary-600">CBS</span>
        <span class="text-neutral-400">|</span>
        <span class="text-neutral-700 font-medium">{{ portalName }}</span>
      </div>
      <nav class="flex items-center gap-4">
        <NuxtLink to="/" class="text-sm text-neutral-600 hover:text-primary-600 font-medium">
          Home
        </NuxtLink>
        <NuxtLink
          to="/nova-admin"
          class="text-sm px-3 py-1.5 rounded-md bg-primary-500 text-white hover:bg-primary-600 font-medium"
        >
          Open CBS Nova Admin
        </NuxtLink>
      </nav>
    </div>
  </header>
</template>
