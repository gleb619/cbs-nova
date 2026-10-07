<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import type { ApiKey } from '../../types/api-keys'

defineProps<{
  apiKeys: ApiKey[]
  loading?: boolean
  error?: string | null
  revokingId?: number | null
  createdSecret?: string | null
}>()

const emit = defineEmits<{
  create: [label: string]
  revoke: [id: number]
  dismissSecret: []
}>()

const newLabel = ref('')
const pendingRevokeId = ref<number | null>(null)
const confirmButtonRef = ref<HTMLButtonElement | null>(null)

const canCreate = computed(() => newLabel.value.trim().length > 0)

function onCreate() {
  const label = newLabel.value.trim()
  if (!label) return
  emit('create', label)
  newLabel.value = ''
}

function onRevoke(apiKey: ApiKey) {
  if (pendingRevokeId.value === apiKey.id) return
  pendingRevokeId.value = apiKey.id
  nextTick(() => confirmButtonRef.value?.focus())
}

function confirmRevoke(apiKey: ApiKey) {
  emit('revoke', apiKey.id)
  pendingRevokeId.value = null
}

function cancelRevoke() {
  pendingRevokeId.value = null
}

function setConfirmButtonRef(id: number, el: HTMLButtonElement | null) {
  if (el && pendingRevokeId.value === id) {
    confirmButtonRef.value = el
  } else if (pendingRevokeId.value === id && confirmButtonRef.value === el) {
    confirmButtonRef.value = null
  }
}

function formatDate(value?: string | null) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}
</script>

<template>
  <div
    data-testid="api-key-list"
    class="flex flex-col h-full bg-white rounded border border-gray-200 overflow-hidden"
  >
    <div class="px-4 py-3 border-b border-gray-200">
      <h2 class="font-semibold text-gray-900">API keys</h2>
      <p class="text-xs text-gray-500 mt-1">
        Long-lived keys used by the DSL auth filter and admin provisioning.
      </p>
    </div>

    <div class="flex-1 overflow-y-auto p-4 space-y-4">
      <div
        v-if="createdSecret"
        data-testid="api-key-secret-banner"
        class="p-3 rounded border border-amber-300 bg-amber-50 text-amber-900 space-y-2"
      >
        <p class="text-sm font-medium">Key created. Copy it now — it is shown exactly once.</p>
        <div class="flex items-center gap-2">
          <code
            data-testid="api-key-secret"
            class="flex-1 block p-2 rounded bg-white border border-amber-200 text-sm break-all"
            >{{
              createdSecret
            }}</code
          >
          <button
            data-testid="api-key-secret-dismiss"
            type="button"
            class="px-3 py-2 rounded bg-amber-200 hover:bg-amber-300 text-amber-900 text-sm font-medium"
            @click="emit('dismissSecret')"
          >
            Dismiss
          </button>
        </div>
      </div>

      <div
        v-if="error"
        data-testid="api-key-error"
        class="p-3 rounded border border-red-300 bg-red-50 text-red-800 text-sm"
      >
        {{ error }}
      </div>

      <form
        data-testid="api-key-create-form"
        class="space-y-3 p-3 rounded border border-gray-200 bg-gray-50"
        @submit.prevent="onCreate"
      >
        <div class="flex flex-col gap-1">
          <label for="api-key-label" class="text-xs font-medium text-gray-700">Name</label>
          <input
            id="api-key-label"
            v-model="newLabel"
            data-testid="api-key-name-input"
            type="text"
            placeholder="e.g. CI deployment"
            class="px-3 py-2 rounded border border-gray-300 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          >
        </div>
        <div class="flex justify-end">
          <button
            data-testid="api-key-create"
            type="submit"
            :disabled="!canCreate || loading"
            class="px-4 py-2 rounded bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Create key
          </button>
        </div>
      </form>

      <div class="border border-gray-200 rounded overflow-hidden">
        <table class="w-full text-sm text-left">
          <thead class="bg-gray-50 text-gray-700 border-b border-gray-200">
            <tr>
              <th class="px-4 py-2 font-medium">Name</th>
              <th class="px-4 py-2 font-medium">Prefix</th>
              <th class="px-4 py-2 font-medium">Created</th>
              <th class="px-4 py-2 font-medium">Last used</th>
              <th class="px-4 py-2 font-medium">Revoked</th>
              <th class="px-4 py-2 font-medium text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="apiKey in apiKeys"
              :key="apiKey.id"
              data-testid="api-key-row"
              class="border-b border-gray-100 last:border-b-0"
            >
              <td data-testid="api-key-name" class="px-4 py-3 font-medium text-gray-900">
                {{ apiKey.label }}
              </td>
              <td data-testid="api-key-prefix" class="px-4 py-3 text-gray-600 font-mono">
                {{ apiKey.prefix }}
              </td>
              <td data-testid="api-key-created-at" class="px-4 py-3 text-gray-600">
                {{ formatDate(apiKey.createdAt) }}
              </td>
              <td data-testid="api-key-last-used-at" class="px-4 py-3 text-gray-600">
                {{ formatDate(apiKey.lastUsedAt) }}
              </td>
              <td data-testid="api-key-revoked-at" class="px-4 py-3 text-gray-600">
                <span
                  v-if="apiKey.revokedAt"
                  data-testid="api-key-revoked-badge"
                  class="inline-flex px-2 py-0.5 rounded bg-neutral-200 text-neutral-700 text-xs"
                >
                  {{ formatDate(apiKey.revokedAt) }}
                </span>
                <span v-else class="text-gray-400">—</span>
              </td>
              <td class="px-4 py-3 text-right">
                <div
                  v-if="pendingRevokeId === apiKey.id"
                  data-testid="api-key-revoke-confirm-group"
                  class="inline-flex items-center gap-2"
                >
                  <span class="text-xs text-red-600">Revoke?</span>
                  <button
                    :ref="(el) => setConfirmButtonRef(apiKey.id, el as HTMLButtonElement | null)"
                    data-testid="api-key-revoke-confirm"
                    type="button"
                    class="px-2 py-1 rounded bg-red-600 text-white text-xs font-medium hover:bg-red-700"
                    @click="confirmRevoke(apiKey)"
                    @keydown.escape="cancelRevoke"
                  >
                    Confirm
                  </button>
                  <button
                    data-testid="api-key-revoke-cancel"
                    type="button"
                    class="px-2 py-1 rounded bg-gray-200 text-gray-700 text-xs font-medium hover:bg-gray-300"
                    @click="cancelRevoke"
                  >
                    Cancel
                  </button>
                </div>
                <button
                  v-else-if="!apiKey.revokedAt"
                  data-testid="api-key-revoke"
                  type="button"
                  :disabled="revokingId === apiKey.id"
                  class="px-3 py-1 rounded border border-red-300 text-red-700 text-xs font-medium hover:bg-red-50 disabled:opacity-50 disabled:cursor-not-allowed"
                  @click="onRevoke(apiKey)"
                >
                  Revoke
                </button>
                <span v-else class="text-xs text-gray-400">Revoked</span>
              </td>
            </tr>
            <tr v-if="apiKeys.length === 0">
              <td colspan="6" class="px-4 py-8 text-center text-gray-500">No API keys found.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
