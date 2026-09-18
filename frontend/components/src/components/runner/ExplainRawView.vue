<script setup lang="ts">
import { computed } from 'vue'

defineOptions({ name: 'ExplainRawView' })

const props = defineProps<{
  markdown: string | undefined
}>()

const text = computed(() => props.markdown ?? '')
</script>

<template>
  <div
    data-testid="explain-raw-view"
    class="h-full overflow-auto font-mono text-xs leading-relaxed"
  >
    <slot v-if="text" name="raw" :text="text">
      <pre
        data-testid="explain-raw-pre"
        class="w-full whitespace-pre-wrap break-words"
      >{{ text }}</pre>
    </slot>
    <p v-else data-testid="explain-raw-empty" class="text-ink-muted text-sm">No markdown source.</p>
  </div>
</template>
