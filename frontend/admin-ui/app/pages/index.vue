<script setup lang="ts">
import { NuxtLink } from '#components'
import type { DefinitionMeta, ExecutionStatus } from '~/types'

interface RecentExecution {
  id: string
  entity: string
  status: ExecutionStatus
  startedAt: string
}

definePageMeta({ layout: 'default' })

const processCount = ref(0)
const transactionCount = ref(0)
const helperCount = ref(0)
const recentExecutions = ref<RecentExecution[]>([])
const loadingDefinitions = ref(false)
const loadingExecutions = ref(false)
const definitionsError = ref<string | null>(null)
const executionsError = ref<string | null>(null)

function extractDefinitions(response: unknown): DefinitionMeta[] {
  if (!response) return []
  if (Array.isArray(response)) return response as DefinitionMeta[]
  const obj = response as {
    definitions?: DefinitionMeta[]
    items?: DefinitionMeta[]
    constructs?: DefinitionMeta[]
  }
  return obj.definitions ?? obj.items ?? obj.constructs ?? []
}

function countByType(definitions: DefinitionMeta[]) {
  processCount.value = definitions.filter((d) => d.type === 'Process').length
  transactionCount.value = definitions.filter((d) => d.type === 'Transaction').length
  helperCount.value = definitions.filter((d) => d.type === 'Helper').length
}

async function loadDefinitions() {
  loadingDefinitions.value = true
  definitionsError.value = null
  try {
    const api = useDslApi()
    const response = await api.getDefinitions()
    countByType(extractDefinitions(response))
  } catch (err) {
    definitionsError.value = (err as Error).message ?? 'Failed to load definitions'
    processCount.value = 0
    transactionCount.value = 0
    helperCount.value = 0
  } finally {
    loadingDefinitions.value = false
  }
}

async function loadRecentExecutions() {
  loadingExecutions.value = true
  executionsError.value = null
  try {
    const api = useExecutionsApi()
    const result = await api.list()
    const items = Array.isArray(result)
      ? result
      : ((result as { items?: RecentExecution[]; data?: RecentExecution[] })?.items ??
        (result as { items?: RecentExecution[]; data?: RecentExecution[] })?.data ??
        [])
    recentExecutions.value = items.slice(0, 5).map((item) => ({
      id: item.id,
      entity: item.entity,
      status: item.status,
      startedAt: item.startedAt,
    }))
  } catch (err) {
    executionsError.value = (err as Error).message ?? 'Failed to load executions'
    recentExecutions.value = []
  } finally {
    loadingExecutions.value = false
  }
}

function onSelectExecution(id: string) {
  navigateTo(`/executions/${id}`)
}

onMounted(() => {
  loadDefinitions()
  loadRecentExecutions()
})
</script>

<template>
  <div class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-gray-900">Dashboard</h1>
      <p class="text-sm text-gray-600 mt-1">Overview of DSL definitions and recent activity.</p>
    </header>

    <section>
      <div v-if="loadingDefinitions" class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div
          v-for="i in 3"
          :key="i"
          class="bg-white rounded-lg shadow-sm border border-gray-200 p-6"
        >
          <div class="flex items-center gap-4">
            <div class="w-10 h-10 bg-gray-200 rounded animate-pulse" />
            <div class="flex-1 space-y-2">
              <div class="h-8 w-16 bg-gray-200 rounded animate-pulse" />
              <div class="h-4 w-24 bg-gray-200 rounded animate-pulse" />
            </div>
          </div>
        </div>
      </div>
      <div v-else class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <DashboardStatCard
          :link-component="NuxtLink"
          label="Processes"
          :count="processCount"
          icon="🔷"
          to="/dsl-workbench"
        />
        <DashboardStatCard
          :link-component="NuxtLink"
          label="Transactions"
          :count="transactionCount"
          icon="🔶"
          to="/dsl-workbench"
        />
        <DashboardStatCard
          :link-component="NuxtLink"
          label="Helpers"
          :count="helperCount"
          icon="🛠"
          to="/dsl-workbench"
        />
      </div>
      <p v-if="definitionsError" class="text-xs text-red-600 mt-2">{{ definitionsError }}</p>
    </section>

    <section>
      <DashboardRecentExecutions
        :executions="recentExecutions"
        :loading="loadingExecutions"
        @select="onSelectExecution"
      />
      <p v-if="executionsError" class="text-xs text-red-600 mt-2">{{ executionsError }}</p>
    </section>
  </div>
</template>
