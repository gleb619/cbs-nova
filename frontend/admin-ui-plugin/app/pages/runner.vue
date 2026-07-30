<script setup lang="ts">
import type { DefinitionMeta, RunnerMode } from '~/types'

definePageMeta({ layout: 'default' })

const route = useRoute()
const router = useRouter()

const {
  selectedDefinition,
  mode,
  status,
  formData,
  mocks,
  output,
  showConfirmModal,
  selectDefinition,
  setMode,
  submit,
  confirmRun,
  resetOutput,
} = useRunner()

const definitions = ref<DefinitionMeta[]>([])
const loadError = ref<string | null>(null)
const loadingDefinitions = ref(false)

async function loadDefinitions() {
  loadingDefinitions.value = true
  loadError.value = null
  try {
    const api = useDslApi()
    const response = (await api.getDefinitions()) as unknown
    const list = extractDefinitions(response)
    definitions.value = list
    syncFromQuery()
  } catch (err) {
    loadError.value = (err as Error).message ?? 'Failed to load definitions'
  } finally {
    loadingDefinitions.value = false
  }
}

function extractDefinitions(response: unknown): DefinitionMeta[] {
  if (!response) return []
  if (Array.isArray(response)) return response as DefinitionMeta[]
  const obj = response as { definitions?: DefinitionMeta[]; items?: DefinitionMeta[] }
  return obj.definitions ?? obj.items ?? []
}

function syncFromQuery() {
  const nameParam = route.query.name
  if (typeof nameParam === 'string' && nameParam) {
    if (selectedDefinition.value !== nameParam) selectDefinition(nameParam)
  } else if (selectedDefinition.value === null && definitions.value.length > 0) {
    selectDefinition(definitions.value[0].name)
  }

  const modeParam = route.query.mode
  if (modeParam === 'preview' || modeParam === 'run' || modeParam === 'explain') {
    setMode(modeParam)
  }
}

watch(
  () => route.query,
  () => syncFromQuery(),
)

function pushQuery(name: string | null, nextMode: RunnerMode) {
  const query: Record<string, string> = {}
  if (name) query.name = name
  if (nextMode) query.mode = nextMode
  router.replace({ query })
}

function onSelectDefinition(name: string) {
  selectDefinition(name)
  pushQuery(name, mode.value)
}

function onSetMode(next: RunnerMode) {
  setMode(next)
  pushQuery(selectedDefinition.value, next)
}

function onSubmit() {
  confirmRun()
}

async function onConfirmRun() {
  showConfirmModal.value = false
  await submit()
}

function onCancelRun() {
  showConfirmModal.value = false
}

const selectedSchema = computed<Record<string, unknown> | undefined>(() => {
  const def = definitions.value.find((d) => d.name === selectedDefinition.value)
  return def?.inputSchema
})

watch(selectedDefinition, () => {
  formData.value = {}
  resetOutput()
})

onMounted(() => {
  loadDefinitions()
})
</script>

<template>
  <div class="flex flex-col gap-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Runner</h1>
        <p class="text-sm text-gray-600">Preview, run, or explain DSL definitions.</p>
      </div>
      <div class="flex items-center gap-3">
        <RunnerStatusIndicator :status="status" />
      </div>
    </header>

    <div class="flex flex-wrap items-end gap-4 bg-white border border-gray-200 rounded-xl p-4">
      <div class="min-w-[16rem] flex-1">
        <RunnerDefinitionSelector
          :definitions="definitions"
          :model-value="selectedDefinition"
          @update:model-value="onSelectDefinition"
        />
        <p v-if="loadingDefinitions" class="text-xs text-gray-500 mt-1">Loading definitions…</p>
        <p v-else-if="loadError" class="text-xs text-red-600 mt-1">{{ loadError }}</p>
      </div>

      <RunnerModeSwitcher :model-value="mode" @update:model-value="onSetMode" />

      <div class="ml-auto flex gap-2">
        <button
          type="button"
          class="px-4 py-2 rounded-lg text-sm font-medium border border-gray-300 text-gray-700 hover:bg-gray-100 disabled:opacity-50"
          :disabled="!selectedDefinition || status === 'loading' || status === 'running'"
          @click="resetOutput"
        >
          Reset
        </button>
        <button
          type="button"
          class="px-4 py-2 rounded-lg text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 disabled:cursor-not-allowed"
          :disabled="!selectedDefinition || status === 'loading' || status === 'running'"
          @click="onSubmit"
        >
          {{ mode === 'run' ? 'Run' : mode === 'explain' ? 'Explain' : 'Preview' }}
        </button>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <section class="bg-white border border-gray-200 rounded-xl p-5">
        <h2 class="text-sm font-semibold text-gray-700 mb-4">Input</h2>
        <RunnerInputForm
          :schema="selectedSchema"
          :model-value="formData"
          @update:model-value="(val) => (formData = val)"
        />
      </section>

      <section class="bg-white border border-gray-200 rounded-xl p-5">
        <h2 class="text-sm font-semibold text-gray-700 mb-4">Output</h2>
        <RunnerOutputPanel :output="output" :mode="mode" :status="status" />
      </section>

      <section
        v-if="mode === 'preview'"
        class="bg-white border border-gray-200 rounded-xl p-5 lg:col-span-2"
      >
        <h2 class="text-sm font-semibold text-gray-700 mb-4">What-if mocks</h2>
        <WhatIfConfigPanel
          :model-value="mocks"
          @update:model-value="(val) => (mocks = val)"
        />
      </section>
    </div>

    <RunnerRunConfirmationModal
      :show="showConfirmModal"
      :payload="formData"
      @confirm="onConfirmRun"
      @cancel="onCancelRun"
    />
  </div>
</template>
