<script setup lang="ts">
import type { FooterLink } from '../AppFooter.vue'

withDefaults(
  defineProps<{
    priority?: 1 | 2 | 3
    links: FooterLink[]
  }>(),
  { priority: 3 },
)
</script>

<template>
  <!-- Priority 3 hides below the md breakpoint (768px); doc links belong on tablets and up, not phones. -->
  <nav
    :class="[
      'flex-wrap items-center justify-center gap-x-4 gap-y-1',
      priority === 3 ? 'hidden md:flex' : priority === 2 ? 'hidden sm:flex' : 'flex',
    ]"
    data-testid="app-footer-links"
    data-priority="3"
    aria-label="Documentation"
  >
    <a
      v-for="link in links"
      :key="link.label"
      :href="link.href"
      target="_blank"
      rel="noopener noreferrer"
      class="hover:text-primary-600"
    >
      {{ link.label }}
    </a>
  </nav>
</template>
