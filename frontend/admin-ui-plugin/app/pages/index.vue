<script setup lang="ts">
import { useDashboardStats } from '@cbs/admin-ui-plugin/composables/useDashboardStats'
import {
  DashboardRecentRunsTable,
  DashboardStatCard,
  ErrorBanner,
} from '@cbs/components'
import { navigateTo } from 'nuxt/app'
import { computed, resolveComponent } from 'vue'

const NuxtLink = resolveComponent('NuxtLink')

const { stats, recentRuns, loading, error, load } = useDashboardStats()

await load()

function statusCount(status: string): number {
  return stats.value?.statusCounts?.[status] ?? 0
}

/** 24h trailing failure rate, rendered as a percentage with one decimal. */
const failureRatePercent = computed(() => {
  const rate = stats.value?.windowFailureRate ?? 0
  return Math.round(rate * 1000) / 10
})

const topProcesses = computed(() => stats.value?.topProcesses ?? [])

function onSelectRun(id: string) {
  navigateTo(`/executions/${id}`)
}
</script>

<template>
  <div class="p-6 space-y-6" data-testid="dashboard">
    <header class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-neutral-900">Dashboard</h1>
    </header>

    <ErrorBanner v-if="error" :message="error" @retry="load" />

    <!-- Stat tiles: counts come from the server-side aggregates endpoint. -->
    <div
      v-if="loading && !stats"
      class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4"
      data-testid="dashboard-stats-skeleton"
    >
      <div
        v-for="i in 5"
        :key="i"
        class="bg-white rounded-lg shadow-sm border border-gray-200 p-6 h-24 animate-pulse"
      />
    </div>
    <div
      v-else
      class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4"
      data-testid="dashboard-stats"
    >
      <DashboardStatCard
        :count="statusCount('Running')"
        label="Running"
        icon="▶"
        to="/executions?status=Running"
        :link-component="NuxtLink"
      />
      <DashboardStatCard
        :count="statusCount('Completed')"
        label="Completed"
        icon="✓"
        to="/executions?status=Completed"
        :link-component="NuxtLink"
      />
      <DashboardStatCard
        :count="statusCount('Failed')"
        label="Failed"
        icon="✕"
        to="/executions?status=Failed"
        :link-component="NuxtLink"
      />
      <DashboardStatCard
        :count="statusCount('Stale')"
        label="Stale"
        icon="⚠"
        to="/executions?status=Stale"
        :link-component="NuxtLink"
      />
      <DashboardStatCard
        :count="failureRatePercent"
        label="Failure rate (24h, %)"
        icon="📉"
        to="/executions?status=Failed"
        :link-component="NuxtLink"
      />
    </div>

    <section class="space-y-2">
      <h2 class="text-lg font-semibold text-neutral-900">Recent runs</h2>
      <DashboardRecentRunsTable
        :executions="recentRuns"
        :loading="loading"
        @select="onSelectRun"
      />
    </section>

    <section v-if="topProcesses.length > 0" class="space-y-2" data-testid="dashboard-top-processes">
      <h2 class="text-lg font-semibold text-neutral-900">Most executed processes</h2>
      <ul class="bg-white border border-gray-200 rounded-lg divide-y divide-gray-100 text-sm">
        <li
          v-for="proc in topProcesses"
          :key="proc.processName"
          :data-testid="`dashboard-top-process-${proc.processName}`"
          class="flex items-center justify-between px-4 py-2"
        >
          <span class="text-neutral-800">{{ proc.processName }}</span>
          <span class="text-neutral-500">{{ proc.runCount }} runs</span>
        </li>
      </ul>
    </section>
  </div>
</template>
