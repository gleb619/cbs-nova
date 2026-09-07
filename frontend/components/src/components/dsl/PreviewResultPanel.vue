<script setup lang="ts">
import ResultTab from '../runner/ResultTab.vue'
import type { RunnerOutput, RunnerStatus } from '../../types/runner'

defineProps<{
  output: RunnerOutput | null
  status: RunnerStatus
  endpoint?: 'preview' | 'run' | 'explain'
}>()
</script>

<template>
  <section class="flex flex-col h-full min-h-0 border border-[#E1E4E8] rounded-sm bg-white">
    <header class="flex items-center justify-between px-3 py-2 border-b border-[#E1E4E8]">
      <span class="text-xs text-[#5A6470]">Result · {{ endpoint ?? 'preview' }}</span>
      <span
        class="text-xs font-mono"
        :class="{
          'text-[#5A6470]': status === 'idle',
          'text-[#1F8F8A]': status === 'success' || status === 'running',
          'text-[#B42318]': status === 'failed',
        }"
      >
        <span v-if="status === 'loading'">running…</span>
        <span v-else-if="status === 'success'">done</span>
        <span v-else-if="status === 'failed'">failed</span>
        <span v-else>idle</span>
      </span>
    </header>

    <div class="flex-1 min-h-0 overflow-auto p-3">
      <div v-if="status === 'loading'" class="text-xs text-[#5A6470] font-mono">request in flight…</div>
      <div v-else-if="output?.errors?.length" class="space-y-1">
        <p
          v-for="(err, i) in output.errors"
          :key="i"
          class="text-xs font-mono text-[#B42318] whitespace-pre-wrap"
        >{{ err.message }}</p>
      </div>
      <ResultTab v-else :result="output?.result" />
    </div>
  </section>
</template>
