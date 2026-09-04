<script setup lang="ts">
withDefaults(
  defineProps<{
    priority?: 1 | 2 | 3
    year: number
    copyright: string
    versionText: string
    buildName: string
    buildTime: string
  }>(),
  { priority: 1 },
)
</script>

<template>
  <!-- priority 1 stays visible at every breakpoint; higher priorities follow the responsive hide/show rules below. -->
  <div
    :class="[
      'flex-wrap items-center gap-x-2 gap-y-1',
      priority === 3 ? 'hidden md:flex' : priority === 2 ? 'hidden sm:flex' : 'flex',
    ]"
    data-testid="app-footer-copyright"
    data-priority="1"
  >
    <span>© {{ year }} {{ copyright }}</span>
    <span v-if="versionText" class="text-neutral-400">| {{ buildName }} v{{ versionText }}</span>
    <span v-if="buildTime" class="text-neutral-400" title="Build time">({{ buildTime }})</span>
  </div>
</template>
