<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ConstructType } from '../../composables/useConstructSchema'
import type { RunnerOutput, RunnerStatus } from '../../types/runner'
import PreviewInputPanel from './PreviewInputPanel.vue'
import PreviewResultPanel from './PreviewResultPanel.vue'

const props = defineProps<{
  name: string
  type?: ConstructType
  endpoint?: 'preview' | 'run' | 'explain'
  preview?: (
    name: string,
    body: unknown,
    metadata?: Record<string, unknown>,
  ) => Promise<RunnerOutput> | RunnerOutput
}>()

const inputJson = ref<string>('{\n  \n}')
const formValue = ref<unknown>(undefined)
const output = ref<RunnerOutput | null>(null)
const status = ref<RunnerStatus>('idle')

function currentPayload(): unknown {
  return formValue.value !== undefined ? formValue.value : JSON.parse(inputJson.value)
}

async function run() {
  status.value = 'loading'
  output.value = null
  try {
    const body = currentPayload()
    const metadata = { startedFrom: 'workbench' }

    if (props.preview) {
      output.value = await props.preview(props.name, body, metadata)
      status.value = 'success'
      return
    }

    const path = props.endpoint ?? 'preview'
    const res = await $fetch<RunnerOutput>(
      `/api/v1/dsl/${path}/${encodeURIComponent(props.name)}`,
      { method: 'POST', body: { body, metadata } },
    )
    output.value = res
    status.value = 'success'
  } catch (err) {
    const e = err as {
      data?: Partial<RunnerOutput> & {
        message?: string
        code?: string
        details?: unknown
        diagnostics?: unknown
      }
      statusMessage?: string
      message?: string
    }
    const data = e.data
    if (data && (Array.isArray(data.errors) || data.message)) {
      // BFF envelope (or legacy {errors:[]}) — surface as-is.
      output.value = {
        ...(data as RunnerOutput),
        errors: Array.isArray(data.errors)
          ? data.errors
          : [
              {
                message: data.message ?? e.statusMessage ?? e.message ?? 'Request failed',
                code: data.code,
              },
            ],
      }
    } else {
      output.value = {
        errors: [
          {
            message: e.statusMessage ?? e.message ?? 'Request failed',
            code: undefined,
          },
        ],
      }
    }
    status.value = 'failed'
  }
}

watch(
  () => props.name,
  () => {
    inputJson.value = '{\n  \n}'
    formValue.value = undefined
  },
)

const inputPanelModel = computed({
  get: () => inputJson.value,
  set: (v: string) => {
    inputJson.value = v
  },
})

watch(inputJson, (v) => {
  try {
    if (v.trim()) {
      formValue.value = JSON.parse(v)
    } else {
      formValue.value = {}
    }
  } catch {
    // leave formValue unchanged while user types invalid JSON
  }
})
</script>

<template>
  <div class="h-full p-3 bg-[#F4F5F7]">
    <div class="grid gap-3 h-full min-h-0 md:grid-cols-2 grid-cols-1">
      <PreviewInputPanel
        v-model="inputPanelModel"
        :name="name"
        :type="type"
        :endpoint="endpoint"
        :busy="status === 'loading'"
        @submit="run"
      />
      <PreviewResultPanel :output="output" :status="status" :endpoint="endpoint" />
    </div>
  </div>
</template>
