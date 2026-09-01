<script lang="ts">
export default { name: "CbsErrorPage" }
</script>

<script setup lang="ts">
// biome-ignore lint/style/useVueMultiWordComponentNames: Nuxt error layout has a single-word filename
import { clearError } from 'nuxt/app'
import { computed } from 'vue'

const props = defineProps<{
  error: {
    statusCode?: number
    statusMessage?: string
    message?: string
  }
}>()

const statusCode = computed(() => props.error?.statusCode ?? 500)
const message = computed(() => {
  const explicit = props.error?.statusMessage ?? props.error?.message
  if (explicit) return explicit
  if (statusCode.value === 404) return 'Page not found'
  return 'Something went wrong'
})

function goHome() {
  clearError({ redirect: '/' })
}
</script>

<template>
  <main class="min-h-screen flex items-center justify-center bg-background px-4 py-10">
    <div
      class="w-full max-w-md rounded-lg border border-neutral-200 bg-white p-6 shadow-sm text-center space-y-4"
      data-testid="error-page"
    >
      <div class="text-xs font-semibold uppercase tracking-wide text-neutral-500">
        CBS Nova Admin
      </div>
      <div class="text-5xl font-bold text-neutral-900">{{ statusCode }}</div>
      <h1 class="text-lg font-semibold text-neutral-800">{{ message }}</h1>
      <button
        type="button"
        class="inline-flex items-center justify-center px-4 py-2 rounded border border-neutral-300 bg-neutral-50 text-sm font-medium text-neutral-800 hover:bg-neutral-100 transition-colors"
        @click="goHome"
      >
        Go to dashboard
      </button>
    </div>
  </main>
</template>
