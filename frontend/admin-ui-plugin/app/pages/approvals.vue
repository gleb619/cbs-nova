<script setup lang="ts">
import { useApprovals } from '@cbs/admin-ui-plugin/composables/useApprovals'
import { useManifestGuard } from '@cbs/admin-ui-plugin/composables/useManifestGuard'
import type { ChangeRequest, ChangeRequestStatus } from '@cbs/components'
import { computed, onMounted, ref } from 'vue'

// T551 — defense-in-depth button guard: the backend re-evaluates the guard at
// execution time; this only disables the decision buttons when denied.
const { allowed: guardAllowed } = useManifestGuard()

const { items, loading, error, load, approve, reject } = useApprovals()

const statusFilter = ref<ChangeRequestStatus | ''>('')

const STATUS_OPTIONS: Array<{ value: ChangeRequestStatus | ''; label: string }> = [
  { value: '', label: 'All' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'SUPERSEDED', label: 'Superseded' },
]

// Pending requests surface first; otherwise newest request wins.
const visibleItems = computed<ChangeRequest[]>(() => {
  const filtered = statusFilter.value
    ? items.value.filter((item) => item.status === statusFilter.value)
    : items.value
  return [...filtered].sort((a, b) => {
    if ((a.status === 'PENDING') !== (b.status === 'PENDING')) {
      return a.status === 'PENDING' ? -1 : 1
    }
    return b.requestedAt.localeCompare(a.requestedAt)
  })
})

const STATUS_BADGE_CLASSES: Record<ChangeRequestStatus, string> = {
  PENDING: 'bg-warning-100 text-warning-800 border-warning-300',
  APPROVED: 'bg-success-100 text-success-800 border-success-300',
  REJECTED: 'bg-error-100 text-error-800 border-error-300',
  SUPERSEDED: 'bg-neutral-100 text-neutral-600 border-neutral-300',
}

function formatRequestedAt(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

// Reject is a two-step inline confirm: first click arms the row, the operator
// types a reason (required by the backend) and confirms.
const pendingRejectId = ref<number | null>(null)
const rejectReason = ref('')

function onReject(request: ChangeRequest) {
  if (pendingRejectId.value === request.id) return
  pendingRejectId.value = request.id
  rejectReason.value = ''
}

function cancelReject() {
  pendingRejectId.value = null
  rejectReason.value = ''
}

async function confirmReject(id: number) {
  const reason = rejectReason.value.trim()
  if (!reason) return
  await reject(id, reason)
  cancelReject()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="p-6 space-y-4 h-full flex flex-col">
    <header class="flex items-start justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-neutral-900">Approvals</h1>
        <p class="text-sm text-neutral-600">
          Change requests submitted from the DSL workbench. Approving a request publishes the draft.
        </p>
      </div>
      <label class="flex items-center gap-2 text-sm text-neutral-700">
        Status
        <select
          v-model="statusFilter"
          class="px-2 py-1.5 border border-line rounded bg-white"
          data-testid="approvals-status-filter"
        >
          <option v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
    </header>

    <div
      v-if="error"
      class="px-3 py-2 text-sm rounded border border-error-300 bg-error-50 text-error-700"
      data-testid="approvals-error"
    >
      {{ error }}
    </div>

    <div v-if="loading" class="text-sm text-neutral-600" data-testid="approvals-loading">
      Loading change requests…
    </div>

    <div
      v-else-if="visibleItems.length === 0"
      class="text-sm text-neutral-600"
      data-testid="approvals-empty"
    >
      No change requests{{ statusFilter ? ` with status ${statusFilter}` : '' }}.
    </div>

    <table v-else class="w-full text-sm border-collapse" data-testid="approvals-table">
      <thead>
        <tr class="text-left text-neutral-600 border-b border-line">
          <th class="py-2 pr-4 font-medium">Definition</th>
          <th class="py-2 pr-4 font-medium">Requested by</th>
          <th class="py-2 pr-4 font-medium">Requested at</th>
          <th class="py-2 pr-4 font-medium">Status</th>
          <th class="py-2 font-medium">Actions</th>
        </tr>
      </thead>
      <tbody>
        <template v-for="request in visibleItems" :key="request.id">
          <tr class="border-b border-line" data-testid="approvals-row">
            <td class="py-2 pr-4 text-neutral-900" data-testid="approvals-definition">
              {{ request.definitionName }}
            </td>
            <td class="py-2 pr-4 text-neutral-700">{{ request.requestedBy }}</td>
            <td class="py-2 pr-4 text-neutral-700">
              {{ formatRequestedAt(request.requestedAt) }}
            </td>
            <td class="py-2 pr-4">
              <span
                class="inline-block px-2 py-0.5 text-xs font-medium rounded-full border"
                :class="STATUS_BADGE_CLASSES[request.status]"
                data-testid="approvals-status"
              >
                {{ request.status }}
              </span>
            </td>
            <td class="py-2">
              <div v-if="request.status === 'PENDING'" class="flex items-center gap-2">
                <button
                  type="button"
                  class="px-2 py-1 text-xs rounded bg-success-500 text-white hover:bg-success-600 disabled:opacity-50 disabled:cursor-not-allowed"
                  :disabled="!guardAllowed('workbench-approve')"
                  data-testid="approvals-approve"
                  @click="approve(request.id)"
                >
                  Approve
                </button>
                <button
                  v-if="pendingRejectId !== request.id"
                  type="button"
                  class="px-2 py-1 text-xs rounded border border-error-300 text-error-700 hover:bg-error-50 disabled:opacity-50 disabled:cursor-not-allowed"
                  :disabled="!guardAllowed('workbench-approve')"
                  data-testid="approvals-reject"
                  @click="onReject(request)"
                >
                  Reject
                </button>
              </div>
              <span v-else-if="request.comment" class="text-xs text-neutral-600">
                {{ request.comment }}
              </span>
            </td>
          </tr>
          <tr v-if="pendingRejectId === request.id" class="border-b border-line bg-surface">
            <td colspan="5" class="py-2">
              <div class="flex items-center gap-2">
                <input
                  v-model="rejectReason"
                  type="text"
                  class="flex-1 px-2 py-1.5 border border-line rounded"
                  placeholder="Reason for rejection (required)"
                  data-testid="approvals-reject-reason"
                >
                <button
                  type="button"
                  class="px-2 py-1 text-xs rounded bg-error-500 text-white hover:bg-error-600 disabled:opacity-50 disabled:cursor-not-allowed"
                  :disabled="!rejectReason.trim()"
                  data-testid="approvals-reject-confirm"
                  @click="confirmReject(request.id)"
                >
                  Confirm rejection
                </button>
                <button
                  type="button"
                  class="px-2 py-1 text-xs rounded border border-line text-neutral-700 hover:bg-white"
                  data-testid="approvals-reject-cancel"
                  @click="cancelReject"
                >
                  Cancel
                </button>
              </div>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>
