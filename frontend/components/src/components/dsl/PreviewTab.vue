<script setup lang="ts">
import { ref } from 'vue'
import type { RunnerOutput, RunnerStatus } from '../../types/runner'
import PreviewInputPanel from './PreviewInputPanel.vue'
import PreviewResultPanel from './PreviewResultPanel.vue'

const props = defineProps<{
  name: string
  endpoint?: 'preview' | 'run' | 'explain'
}>()

const inputJson = ref<string>('{\n  \n}')
const output = ref<RunnerOutput | null>(null)
const status = ref<RunnerStatus>('idle')

async function run() {
  status.value = 'loading'
  output.value = null
  try {
    const path = props.endpoint ?? 'preview'
    const res = await $fetch<RunnerOutput>(
      `/api/v1/dsl/${path}/${encodeURIComponent(props.name)}`,
      { method: 'POST', body: { body: JSON.parse(inputJson.value) } },
    )
    output.value = res
    status.value = 'success'
  } catch (err) {
    const e = err as { data?: RunnerOutput; statusMessage?: string; message?: string }
    output.value = e.data ?? { errors: [{ message: e.statusMessage ?? e.message ?? 'Request failed' }] }
    status.value = 'failed'
  }
}
</script>

<template>
  <div class="h-full p-3 bg-[#F4F5F7]">
    <div class="grid gap-3 h-full min-h-0 md:grid-cols-2 grid-cols-1">
      <PreviewInputPanel
        v-model="inputJson"
        :name="name"
        :endpoint="endpoint"
        :busy="status === 'loading'"
        @submit="run"
      />
      <PreviewResultPanel :output="output" :status="status" :endpoint="endpoint" />
    </div>
  </div>
</template>
