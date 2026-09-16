<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useLocalStorageState } from '../composables/useLocalStorageState'
import type { WebhookDeliveryPage, WebhookDeliveryRecord } from '../types/webhooks'
import ErrorBanner from './ErrorBanner.vue'

const PAGE_SIZE = 25

const SUCCESS_STATUSES = new Set(['delivered'])
const FAILURE_STATUSES = new Set(['rejected', 'serialization_failed', 'failed'])

const props = defineProps<{
  fetchPage: (params: {
    subscriptionId?: string
    limit: number
    offset: number
  }) => Promise<WebhookDeliveryPage>
}>()

const subscriptionFilter = useLocalStorageState('webhooks.filter.subscriptionId', '')

const subscriptionInput = ref(subscriptionFilter.value)
const page = ref<WebhookDeliveryPage | null>(null)
const offset = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)

const rows = computed<WebhookDeliveryRecord[]>(() => page.value?.items ?? [])

const rangeText = computed(() => {
  if (!page.value || page.value.items.length === 0) return ''
  const start = (page.value!.offset ?? 0) + 1
  const end = (page.value!.offset ?? 0) + page.value.items.length
  return `${start}–${end} of ${page.value!.total ?? 0}`
})

const canGoPrev = computed(() => offset.value > 0)
const canGoNext = computed(() => {
  if (!page.value) return false
  return page.value!.offset + page.value.items.length < (page.value!.total ?? 0)
})

async function load(targetOffset: number) {
  if (loading.value) return
  loading.value = true
  error.value = null
  try {
    page.value = await props.fetchPage({
      subscriptionId: subscriptionFilter.value,
      limit: PAGE_SIZE,
      offset: targetOffset,
    })
    offset.value = targetOffset
  } catch (err) {
    error.value = (err as Error).message
    page.value = null
  } finally {
    loading.value = false
  }
}

function applySubscriptionFilter() {
  subscriptionFilter.value = subscriptionInput.value.trim()
  void load(0)
}

function clearSubscriptionFilter() {
  subscriptionInput.value = ''
  applySubscriptionFilter()
}

function refresh() {
  void load(offset.value)
}

function goPrev() {
  if (!canGoPrev.value) return
  void load(Math.max(0, offset.value - PAGE_SIZE))
}

function goNext() {
  if (!canGoNext.value) return
  void load(offset.value + PAGE_SIZE)
}

function statusClass(status: string): string {
  const normalized = status.trim().toLowerCase()
  if (SUCCESS_STATUSES.has(normalized)) return 'bg-green-100 text-green-800'
  if (FAILURE_STATUSES.has(normalized)) return 'bg-red-100 text-red-800'
  const code = Number(normalized)
  if (Number.isInteger(code) && code > 0) {
    if (code >= 200 && code < 400) return 'bg-green-100 text-green-800'
    if (code >= 400 && code < 600) return 'bg-red-100 text-red-800'
  }
  return 'bg-gray-100 text-gray-800'
}

function displayUrl(url: string): string {
  try {
    const parsed = new URL(url)
    return parsed.host + parsed.pathname
  } catch {
    return url
  }
}

function formatRelativeTime(iso: string): string {
  const elapsed = Date.now() - new Date(iso).getTime()
  if (Number.isNaN(elapsed)) return iso
  const seconds = Math.max(0, Math.round(elapsed / 1000))
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.round(hours / 24)}d ago`
}

onMounted(() => {
  void load(0)
})
</script>

<template>
  <div class="flex flex-col h-full" data-testid="webhook-deliveries-panel">
    <div class="px-4 py-3 border-b border-gray-200">
      <h2 class="text-base font-semibold text-gray-900">Webhook deliveries</h2>
      <p class="text-xs text-gray-500 mt-0.5">
        Delivery outcomes recorded for run-completion webhook subscriptions, newest first.
      </p>
    </div>

    <div class="px-4 py-2 border-b border-gray-200 flex flex-wrap items-center gap-2">
      <form class="flex items-center gap-1" @submit.prevent="applySubscriptionFilter">
        <input
          v-model="subscriptionInput"
          type="text"
          placeholder="Filter by subscription"
          class="px-2 py-1 text-sm border border-gray-300 rounded"
          data-testid="webhook-deliveries-filter-subscription"
        >
        <button
          type="submit"
          class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100"
          data-testid="webhook-deliveries-filter-apply"
        >
          Apply
        </button>
        <button
          v-if="subscriptionFilter"
          type="button"
          class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100"
          data-testid="webhook-deliveries-filter-clear"
          @click="clearSubscriptionFilter"
        >
          Clear
        </button>
      </form>
      <button
        type="button"
        class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50"
        :disabled="loading"
        data-testid="webhook-deliveries-refresh"
        @click="refresh"
      >
        Refresh
      </button>
    </div>

    <div class="flex-1 overflow-auto">
      <div
        v-if="loading"
        class="px-4 py-3 text-sm text-gray-500"
        data-testid="webhook-deliveries-loading"
      >
        Loading webhook deliveries…
      </div>
      <div v-else-if="error" class="px-4 py-3" data-testid="webhook-deliveries-error">
        <ErrorBanner :message="error" retry-label="Retry" @retry="refresh" />
      </div>
      <div
        v-else-if="rows.length === 0"
        class="px-4 py-3 text-sm text-gray-500"
        data-testid="webhook-deliveries-empty"
      >
        No webhook deliveries recorded.
      </div>
      <table v-else class="w-full text-sm" data-testid="webhook-deliveries-table">
        <thead>
          <tr class="text-left text-xs uppercase text-gray-500 border-b border-gray-200">
            <th class="px-4 py-2 font-semibold">When</th>
            <th class="px-2 py-2 font-semibold">Subscription</th>
            <th class="px-2 py-2 font-semibold">URL</th>
            <th class="px-2 py-2 font-semibold">Status</th>
            <th class="px-2 py-2 font-semibold">Attempts</th>
            <th class="px-2 py-2 font-semibold">Last error</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr
            v-for="item in rows"
            :key="item.id"
            class="align-top"
            data-testid="webhook-deliveries-row"
          >
            <td class="px-4 py-2 whitespace-nowrap text-gray-500" :title="item.occurredAt">
              {{ formatRelativeTime(item.occurredAt) }}
            </td>
            <td
              class="px-2 py-2 font-medium text-gray-900"
              data-testid="webhook-deliveries-subscription"
            >
              {{ item.subscriptionId }}
            </td>
            <td class="px-2 py-2 text-gray-600 break-all" data-testid="webhook-deliveries-url">
              {{ displayUrl(item.url) }}
            </td>
            <td class="px-2 py-2">
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium"
                :class="statusClass(item.status)"
                data-testid="webhook-deliveries-status"
              >
                {{ item.status }}
              </span>
            </td>
            <td class="px-2 py-2 text-gray-600" data-testid="webhook-deliveries-attempts">
              {{ item.attempts }}
            </td>
            <td
              class="px-2 py-2 text-gray-700 max-w-xs truncate"
              :title="item.lastError ?? ''"
              data-testid="webhook-deliveries-last-error"
            >
              {{ item.lastError ?? '' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      class="px-4 py-2 border-t border-gray-200 flex items-center justify-between text-xs text-gray-500"
      data-testid="webhook-deliveries-pager"
    >
      <span data-testid="webhook-deliveries-range">{{ rangeText }}</span>
      <span class="flex items-center gap-1">
        <button
          type="button"
          class="px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!canGoPrev || loading"
          data-testid="webhook-deliveries-pager-prev"
          @click="goPrev"
        >
          Prev
        </button>
        <button
          type="button"
          class="px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!canGoNext || loading"
          data-testid="webhook-deliveries-pager-next"
          @click="goNext"
        >
          Next
        </button>
      </span>
    </div>
  </div>
</template>
