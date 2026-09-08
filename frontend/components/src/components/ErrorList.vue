<script setup lang="ts">
import { reactive } from 'vue'

interface ErrorItem {
  message: string
  code?: string
  stackTrace?: string
}

withDefaults(
  defineProps<{
    items?: ErrorItem[]
    emptyText?: string
    showStackTrace?: boolean
    codeStyle?: 'badge' | 'text'
    variant?: 'runner' | 'executions'
  }>(),
  {
    emptyText: 'No errors.',
    showStackTrace: false,
    codeStyle: 'badge',
    variant: 'runner',
  },
)

const expanded = reactive<Record<number, boolean>>({})

function toggle(i: number) {
  expanded[i] = !expanded[i]
}

function isExpanded(i: number): boolean {
  return Boolean(expanded[i])
}
</script>

<template>
  <div data-testid="error-list">
    <template v-if="variant === 'runner'">
      <div v-if="!items || items.length === 0" class="text-sm text-gray-500">{{ emptyText }}</div>
      <ul v-else class="space-y-2">
        <li
          v-for="(err, idx) in items"
          :key="idx"
          class="border border-red-200 bg-red-50 rounded-lg p-3"
          data-testid="runner-errors-row"
        >
          <div :data-testid="`error-list-row-${idx}`" class="contents">
            <div v-if="err.code" class="flex items-center gap-2 mb-1">
              <span
                v-if="codeStyle === 'badge'"
                class="text-xs font-mono px-2 py-0.5 rounded bg-red-200 text-red-800"
                >{{ err.code }}</span
              >
              <span v-else class="text-xs text-gray-500"
                >Code: <span class="font-mono">{{ err.code }}</span></span
              >
            </div>
            <p class="text-sm text-red-900 whitespace-pre-wrap break-words">{{ err.message }}</p>
          </div>
        </li>
      </ul>
    </template>
    <template v-else-if="variant === 'executions'">
      <div
        v-if="!items || items.length === 0"
        class="bg-white border border-gray-200 rounded-lg p-12 text-center text-sm text-gray-500"
      >
        {{ emptyText }}
      </div>
      <div v-else class="space-y-3">
        <div
          v-for="(err, idx) in items"
          :key="idx"
          :data-testid="`executions-errors-row-${idx}`"
          class="bg-white border border-red-200 rounded-lg p-4"
        >
          <div :data-testid="`error-list-row-${idx}`" class="contents">
            <div class="flex items-start gap-3">
              <span class="text-red-500 text-lg">⚠</span>
              <div class="flex-1 min-w-0">
                <div class="text-sm font-semibold text-red-700 break-all">{{ err.message }}</div>
                <div v-if="err.code" class="text-xs text-gray-500 mt-1">
                  <template v-if="codeStyle === 'badge'">
                    <span class="text-xs font-mono px-2 py-0.5 rounded bg-red-200 text-red-800"
                      >{{ err.code }}</span
                    >
                  </template>
                  <template v-else> Code: <span class="font-mono">{{ err.code }}</span> </template>
                </div>
                <div v-if="showStackTrace && err.stackTrace" class="mt-2">
                  <button
                    type="button"
                    class="text-xs text-blue-600 hover:underline"
                    @click="toggle(idx)"
                  >
                    {{ isExpanded(idx) ? 'Hide stack trace' : 'Show stack trace' }}
                  </button>
                  <pre
                    v-if="isExpanded(idx)"
                    class="mt-2 text-[11px] bg-gray-50 border border-gray-200 rounded p-2 overflow-auto max-h-64 whitespace-pre"
                  ><code>{{ err.stackTrace }}</code></pre>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
