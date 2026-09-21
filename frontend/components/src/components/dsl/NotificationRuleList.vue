<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import type {
  CreateNotificationRulePayload,
  NotificationFireLogPage,
  NotificationRule,
  NotificationSinkType,
  NotificationTestPayload,
  NotificationTestResult,
} from '../../types/notifications'

const PAGE_SIZE = 25

const EVENT_TYPES = [
  'RunStarted',
  'RunCompleted',
  'RunFailed',
  'RunCancelled',
  'RunStale',
  'DraftSaved',
  'DraftPublished',
  'PieceNotified',
]

const SINK_TYPES: NotificationSinkType[] = ['webhook', 'email', 'slack', 'pagerduty']

const props = defineProps<{
  rules: NotificationRule[]
  loading?: boolean
  error?: string | null
  testing?: boolean
  testResult?: NotificationTestResult | null
  fireLog?: NotificationFireLogPage | null
  fireLogLoading?: boolean
  fireLogError?: string | null
}>()

const emit = defineEmits<{
  create: [payload: CreateNotificationRulePayload]
  update: [id: number, payload: CreateNotificationRulePayload]
  delete: [id: number]
  toggle: [id: number, enabled: boolean]
  test: [payload: NotificationTestPayload]
  loadFireLog: [params: { offset: number; limit: number }]
}>()

// ---------------------------------------------------------------------------
// Create / edit form
// ---------------------------------------------------------------------------

const name = ref('')
const eventType = ref(EVENT_TYPES[0])
const definitionPattern = ref('')
const status = ref('')
const sink = ref<NotificationSinkType>('webhook')
const url = ref('')
const secret = ref('')
const to = ref('')
const priority = ref('0')
const rateClass = ref('default')

const editingId = ref<number | null>(null)

const pendingConfirm = ref<number | null>(null)
const confirmButtonRef = ref<HTMLButtonElement | null>(null)

const formError = ref('')

const isEmail = computed(() => sink.value === 'email')

const canSubmit = computed(() => {
  if (name.value.trim().length === 0) return false
  if (isEmail.value) return to.value.trim().length > 0
  return url.value.trim().length > 0
})

function buildAction() {
  if (isEmail.value) {
    return { sink: sink.value, to: to.value.trim() || null }
  }
  const action: { sink: NotificationSinkType; url?: string | null; secret?: string | null } = {
    sink: sink.value,
    url: url.value.trim() || null,
  }
  if (sink.value === 'webhook' && secret.value.trim()) {
    action.secret = secret.value.trim()
  }
  return action
}

function buildPayload(): CreateNotificationRulePayload {
  const payload: CreateNotificationRulePayload = {
    name: name.value.trim(),
    eventFilter: {
      eventType: eventType.value,
      definitionPattern: definitionPattern.value.trim() || null,
      status: status.value.trim() || null,
    },
    actions: [buildAction()],
    priority: Number(priority.value) || 0,
    rateClass: rateClass.value.trim() || 'default',
  }
  if (editingId.value === null) {
    payload.enabled = true
  }
  return payload
}

function resetForm() {
  name.value = ''
  eventType.value = EVENT_TYPES[0]
  definitionPattern.value = ''
  status.value = ''
  sink.value = 'webhook'
  url.value = ''
  secret.value = ''
  to.value = ''
  priority.value = '0'
  rateClass.value = 'default'
  editingId.value = null
  formError.value = ''
}

function onSubmit() {
  formError.value = ''
  if (!canSubmit.value) {
    formError.value = isEmail.value
      ? 'Email sink requires a recipient (to).'
      : 'Sink requires a target URL.'
    return
  }
  const payload = buildPayload()
  if (editingId.value === null) {
    emit('create', payload)
  } else {
    emit('update', editingId.value, payload)
  }
  resetForm()
}

function startEdit(rule: NotificationRule) {
  editingId.value = rule.id
  name.value = rule.name
  eventType.value = rule.eventFilter.eventType
  definitionPattern.value = rule.eventFilter.definitionPattern ?? ''
  status.value = rule.eventFilter.status ?? ''
  const action = rule.actions[0]
  sink.value = action?.sink ?? 'webhook'
  url.value = action?.url ?? ''
  secret.value = ''
  to.value = action?.to ?? ''
  priority.value = String(rule.priority ?? 0)
  rateClass.value = rule.rateClass ?? 'default'
  formError.value = ''
  pendingConfirm.value = null
}

function cancelEdit() {
  resetForm()
}

// ---------------------------------------------------------------------------
// Delete (two-step confirm)
// ---------------------------------------------------------------------------

function onDelete(rule: NotificationRule) {
  if (pendingConfirm.value === rule.id) {
    return
  }
  pendingConfirm.value = rule.id
  nextTick(() => {
    confirmButtonRef.value?.focus()
  })
}

function confirmDelete(rule: NotificationRule) {
  emit('delete', rule.id)
  pendingConfirm.value = null
}

function cancelDelete() {
  pendingConfirm.value = null
}

function setConfirmButtonRef(ruleId: number, el: HTMLButtonElement | null) {
  if (el && pendingConfirm.value === ruleId) {
    confirmButtonRef.value = el
  } else if (pendingConfirm.value === ruleId && confirmButtonRef.value === el) {
    confirmButtonRef.value = null
  }
}

function onPendingKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    cancelDelete()
  }
}

// ---------------------------------------------------------------------------
// Test event form
// ---------------------------------------------------------------------------

const testEventType = ref(EVENT_TYPES[0])
const testProcessName = ref('')
const testStatus = ref('')
const testRunId = ref('')

function onTest() {
  const payload: NotificationTestPayload = { eventType: testEventType.value }
  if (testProcessName.value.trim()) payload.processName = testProcessName.value.trim()
  if (testStatus.value.trim()) payload.status = testStatus.value.trim()
  if (testRunId.value.trim()) payload.runId = testRunId.value.trim()
  emit('test', payload)
}

// ---------------------------------------------------------------------------
// Fire log
// ---------------------------------------------------------------------------

const fireLogRows = computed(() => props.fireLog?.items ?? [])
const fireLogOffset = computed(() => props.fireLog?.offset ?? 0)

const fireLogRange = computed(() => {
  if (!props.fireLog || props.fireLog.items.length === 0) return ''
  const start = (props.fireLog.offset ?? 0) + 1
  const end = (props.fireLog.offset ?? 0) + props.fireLog.items.length
  return `${start}–${end} of ${props.fireLog.total ?? 0}`
})

const canGoPrev = computed(() => fireLogOffset.value > 0)
const canGoNext = computed(() => {
  if (!props.fireLog) return false
  return props.fireLog.offset + props.fireLog.items.length < (props.fireLog.total ?? 0)
})

function goPrev() {
  if (!canGoPrev.value) return
  emit('loadFireLog', { offset: Math.max(0, fireLogOffset.value - PAGE_SIZE), limit: PAGE_SIZE })
}

function goNext() {
  if (!canGoNext.value) return
  emit('loadFireLog', { offset: fireLogOffset.value + PAGE_SIZE, limit: PAGE_SIZE })
}

function refreshFireLog() {
  emit('loadFireLog', { offset: fireLogOffset.value, limit: PAGE_SIZE })
}

function outcomeClass(outcome: string): string {
  const normalized = outcome.trim().toLowerCase()
  if (normalized === 'delivered' || normalized === 'success') return 'bg-green-100 text-green-800'
  if (normalized === 'failed' || normalized === 'error') return 'bg-red-100 text-red-800'
  return 'bg-gray-100 text-gray-800'
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
</script>

<template>
  <div
    data-testid="notification-rule-list"
    class="flex flex-col h-full bg-white rounded border border-gray-200 overflow-hidden"
  >
    <div class="px-4 py-3 border-b border-gray-200">
      <h2 class="font-semibold text-gray-900">Notification rules</h2>
      <p class="text-xs text-gray-500 mt-1">
        Rules that match domain events and fan out to notification sinks.
      </p>
    </div>

    <div class="flex-1 overflow-y-auto p-4 space-y-4">
      <form
        data-testid="notification-rule-create-form"
        class="space-y-3 p-3 rounded border border-gray-200 bg-gray-50"
        @submit.prevent="onSubmit"
      >
        <div class="flex items-center justify-between">
          <h3 class="text-sm font-medium text-gray-800">
            {{ editingId === null ? 'Create rule' : `Edit rule #${editingId}` }}
          </h3>
          <button
            v-if="editingId !== null"
            type="button"
            data-testid="notification-rule-edit-cancel"
            class="px-2 py-1 text-xs rounded border border-gray-300 text-gray-700 hover:bg-gray-100"
            @click="cancelEdit"
          >
            Cancel
          </button>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div class="flex flex-col gap-1">
            <label for="notification-rule-name" class="text-xs font-medium text-gray-700"
              >Name</label
            >
            <input
              id="notification-rule-name"
              v-model="name"
              data-testid="notification-rule-name-input"
              type="text"
              placeholder="onboarding-failures"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
          <div class="flex flex-col gap-1">
            <label for="notification-rule-event-type" class="text-xs font-medium text-gray-700"
              >Event type</label
            >
            <select
              id="notification-rule-event-type"
              v-model="eventType"
              data-testid="notification-rule-event-type-input"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
              <option v-for="type in EVENT_TYPES" :key="type" :value="type">{{ type }}</option>
            </select>
          </div>
          <div class="flex flex-col gap-1">
            <label
              for="notification-rule-definition-pattern"
              class="text-xs font-medium text-gray-700"
              >Definition pattern (optional)</label
            >
            <input
              id="notification-rule-definition-pattern"
              v-model="definitionPattern"
              data-testid="notification-rule-definition-pattern-input"
              type="text"
              placeholder="Loan*"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
          <div class="flex flex-col gap-1">
            <label for="notification-rule-status" class="text-xs font-medium text-gray-700"
              >Status (optional)</label
            >
            <input
              id="notification-rule-status"
              v-model="status"
              data-testid="notification-rule-status-input"
              type="text"
              placeholder="FAILED"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
          <div class="flex flex-col gap-1">
            <label for="notification-rule-sink" class="text-xs font-medium text-gray-700"
              >Sink</label
            >
            <select
              id="notification-rule-sink"
              v-model="sink"
              data-testid="notification-rule-sink-input"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
              <option v-for="type in SINK_TYPES" :key="type" :value="type">{{ type }}</option>
            </select>
          </div>
          <div v-if="isEmail" class="flex flex-col gap-1">
            <label for="notification-rule-to" class="text-xs font-medium text-gray-700"
              >Recipient (to)</label
            >
            <input
              id="notification-rule-to"
              v-model="to"
              data-testid="notification-rule-to-input"
              type="text"
              placeholder="oncall@example.com"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
          <template v-else>
            <div class="flex flex-col gap-1">
              <label for="notification-rule-url" class="text-xs font-medium text-gray-700"
                >URL</label
              >
              <input
                id="notification-rule-url"
                v-model="url"
                data-testid="notification-rule-url-input"
                type="text"
                placeholder="https://hooks.example.com/notify"
                class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
              >
            </div>
            <div v-if="sink === 'webhook'" class="flex flex-col gap-1">
              <label for="notification-rule-secret" class="text-xs font-medium text-gray-700"
                >Secret (optional)</label
              >
              <input
                id="notification-rule-secret"
                v-model="secret"
                data-testid="notification-rule-secret-input"
                type="password"
                placeholder="write-only"
                class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
              >
            </div>
          </template>
          <div class="flex flex-col gap-1">
            <label for="notification-rule-priority" class="text-xs font-medium text-gray-700"
              >Priority</label
            >
            <input
              id="notification-rule-priority"
              v-model="priority"
              data-testid="notification-rule-priority-input"
              type="number"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
          <div class="flex flex-col gap-1">
            <label for="notification-rule-rate-class" class="text-xs font-medium text-gray-700"
              >Rate class</label
            >
            <input
              id="notification-rule-rate-class"
              v-model="rateClass"
              data-testid="notification-rule-rate-class-input"
              type="text"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
        </div>
        <span
          v-if="formError"
          data-testid="notification-rule-form-error"
          class="text-xs text-red-600"
          >{{ formError }}</span
        >
        <button
          type="submit"
          data-testid="notification-rule-submit"
          :disabled="!canSubmit"
          class="px-3 py-1.5 text-sm rounded bg-blue-600 text-white disabled:opacity-50 disabled:cursor-not-allowed hover:bg-blue-700"
        >
          {{ editingId === null ? 'Create Rule' : 'Save Rule' }}
        </button>
      </form>

      <div v-if="loading" class="space-y-3" data-testid="notification-rule-list-loading">
        <div v-for="i in 3" :key="i" class="h-16 bg-gray-100 rounded animate-pulse" />
      </div>

      <div
        v-else-if="error"
        class="text-sm text-red-600"
        data-testid="notification-rule-list-error"
      >
        {{ error }}
      </div>

      <div
        v-else-if="rules.length === 0"
        class="text-sm text-gray-500 italic text-center py-8"
        data-testid="notification-rule-list-empty"
      >
        No notification rules configured.
      </div>

      <ul v-else class="space-y-2">
        <li
          v-for="rule in rules"
          :key="rule.id"
          data-testid="notification-rule-row"
          class="px-3 py-2 rounded border border-gray-100 hover:bg-gray-50 flex items-center justify-between gap-3"
        >
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <span class="font-medium text-gray-900 truncate">{{ rule.name }}</span>
              <span
                class="text-[10px] px-1.5 py-0.5 rounded-full bg-blue-100 text-blue-700"
                data-testid="notification-rule-event-type"
              >
                {{ rule.eventFilter.eventType }}
              </span>
              <span
                v-if="!rule.enabled"
                class="text-[10px] px-1.5 py-0.5 rounded-full bg-gray-100 text-gray-700"
              >
                disabled
              </span>
            </div>
            <div class="text-xs text-gray-500 mt-0.5">
              <span v-if="rule.eventFilter.definitionPattern"
                >definition {{ rule.eventFilter.definitionPattern }}</span
              >
              <span v-if="rule.eventFilter.status">· status {{ rule.eventFilter.status }}</span>
              <span v-if="rule.eventFilter.definitionPattern || rule.eventFilter.status"> · </span>
              sinks {{ rule.actions.map((a) => a.sink).join(', ') || 'none' }}
              · priority {{ rule.priority }} · {{ rule.rateClass }}
            </div>
          </div>
          <button
            type="button"
            data-testid="notification-rule-toggle"
            :disabled="editingId === rule.id"
            class="px-2 py-1 text-xs rounded border shrink-0 disabled:opacity-50 disabled:cursor-not-allowed"
            :class="
              rule.enabled
                ? 'border-green-300 text-green-700 hover:bg-green-50'
                : 'border-gray-300 text-gray-700 hover:bg-gray-100'
            "
            :title="rule.enabled ? 'Disable rule' : 'Enable rule'"
            @click="emit('toggle', rule.id, !rule.enabled)"
          >
            {{ rule.enabled ? 'Enabled' : 'Disabled' }}
          </button>
          <button
            type="button"
            data-testid="notification-rule-edit"
            class="px-2 py-1 text-xs rounded border border-gray-300 text-gray-700 hover:bg-gray-100 shrink-0"
            @click="startEdit(rule)"
          >
            Edit
          </button>
          <button
            v-if="pendingConfirm !== rule.id"
            type="button"
            data-testid="notification-rule-delete"
            class="px-2 py-1 text-xs rounded border border-red-300 text-red-700 hover:bg-red-50 shrink-0"
            @click="onDelete(rule)"
          >
            Delete
          </button>
          <fieldset
            v-else
            class="flex items-center gap-1 shrink-0 border-0 p-0 m-0"
            data-testid="notification-rule-delete-confirm-group"
            @keydown="onPendingKeydown"
          >
            <button
              :ref="(el) => setConfirmButtonRef(rule.id, el as HTMLButtonElement | null)"
              type="button"
              data-testid="notification-rule-delete-confirm"
              class="px-2 py-1 text-xs rounded bg-red-600 text-white hover:bg-red-700"
              @click="confirmDelete(rule)"
            >
              Confirm
            </button>
            <button
              type="button"
              data-testid="notification-rule-delete-cancel"
              class="px-2 py-1 text-xs rounded border border-gray-300 text-gray-700 hover:bg-gray-100"
              @click="cancelDelete"
            >
              Cancel
            </button>
          </fieldset>
        </li>
      </ul>

      <form
        data-testid="notification-test-form"
        class="space-y-3 p-3 rounded border border-gray-200 bg-gray-50"
        @submit.prevent="onTest"
      >
        <h3 class="text-sm font-medium text-gray-800">Send test event</h3>
        <div class="grid grid-cols-1 md:grid-cols-4 gap-3">
          <div class="flex flex-col gap-1">
            <label for="notification-test-event-type" class="text-xs font-medium text-gray-700"
              >Event type</label
            >
            <select
              id="notification-test-event-type"
              v-model="testEventType"
              data-testid="notification-test-event-type-input"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
              <option v-for="type in EVENT_TYPES" :key="type" :value="type">{{ type }}</option>
            </select>
          </div>
          <div class="flex flex-col gap-1">
            <label for="notification-test-process-name" class="text-xs font-medium text-gray-700"
              >Process name (optional)</label
            >
            <input
              id="notification-test-process-name"
              v-model="testProcessName"
              data-testid="notification-test-process-name-input"
              type="text"
              placeholder="LoanDisbursement"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
          <div class="flex flex-col gap-1">
            <label for="notification-test-status" class="text-xs font-medium text-gray-700"
              >Status (optional)</label
            >
            <input
              id="notification-test-status"
              v-model="testStatus"
              data-testid="notification-test-status-input"
              type="text"
              placeholder="FAILED"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
          <div class="flex flex-col gap-1">
            <label for="notification-test-run-id" class="text-xs font-medium text-gray-700"
              >Run ID (optional)</label
            >
            <input
              id="notification-test-run-id"
              v-model="testRunId"
              data-testid="notification-test-run-id-input"
              type="text"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
        </div>
        <button
          type="submit"
          data-testid="notification-test-submit"
          :disabled="testing"
          class="px-3 py-1.5 text-sm rounded bg-blue-600 text-white disabled:opacity-50 disabled:cursor-not-allowed hover:bg-blue-700"
        >
          {{ testing ? 'Sending…' : 'Send Test Event' }}
        </button>
        <div v-if="testResult" data-testid="notification-test-result" class="space-y-1">
          <p class="text-xs text-gray-600" data-testid="notification-test-matched">
            Matched {{ testResult.matchedRuleIds.length }} rule(s):
            {{ testResult.matchedRuleIds.join(', ') || 'none' }}
          </p>
          <ul v-if="testResult.fireResults.length > 0" class="space-y-1">
            <li
              v-for="result in testResult.fireResults"
              :key="`${result.ruleId}-${result.sink}`"
              data-testid="notification-test-fire-result"
              class="text-xs px-2 py-1 rounded border border-gray-200 flex items-center gap-2"
            >
              <span class="font-medium text-gray-800"
                >#{{ result.ruleId }} {{ result.ruleName }}</span
              >
              <span class="text-gray-500">{{ result.sink }}</span>
              <span
                class="inline-flex items-center px-1.5 py-0.5 rounded-full text-[10px] font-medium"
                :class="outcomeClass(result.outcome)"
              >
                {{ result.outcome }}
              </span>
              <span v-if="result.detail" class="text-gray-500 truncate">{{ result.detail }}</span>
            </li>
          </ul>
        </div>
      </form>

      <div data-testid="notification-fire-log" class="rounded border border-gray-200">
        <div class="px-3 py-2 border-b border-gray-200 flex items-center justify-between">
          <h3 class="text-sm font-medium text-gray-800">Firing log</h3>
          <button
            type="button"
            data-testid="notification-fire-log-refresh"
            class="px-2 py-1 text-xs rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50"
            :disabled="fireLogLoading"
            @click="refreshFireLog"
          >
            Refresh
          </button>
        </div>
        <div
          v-if="fireLogLoading"
          class="px-3 py-3 text-sm text-gray-500"
          data-testid="notification-fire-log-loading"
        >
          Loading firing log…
        </div>
        <div
          v-else-if="fireLogError"
          class="px-3 py-3 text-sm text-red-600"
          data-testid="notification-fire-log-error"
        >
          {{ fireLogError }}
        </div>
        <div
          v-else-if="fireLogRows.length === 0"
          class="px-3 py-3 text-sm text-gray-500"
          data-testid="notification-fire-log-empty"
        >
          No firings recorded.
        </div>
        <table v-else class="w-full text-sm" data-testid="notification-fire-log-table">
          <thead>
            <tr class="text-left text-xs uppercase text-gray-500 border-b border-gray-200">
              <th class="px-3 py-2 font-semibold">When</th>
              <th class="px-2 py-2 font-semibold">Rule</th>
              <th class="px-2 py-2 font-semibold">Sink</th>
              <th class="px-2 py-2 font-semibold">Outcome</th>
              <th class="px-2 py-2 font-semibold">Detail</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr
              v-for="firing in fireLogRows"
              :key="firing.id"
              class="align-top"
              data-testid="notification-fire-log-row"
            >
              <td class="px-3 py-2 whitespace-nowrap text-gray-500" :title="firing.createdAt">
                {{ formatRelativeTime(firing.createdAt) }}
              </td>
              <td
                class="px-2 py-2 font-medium text-gray-900"
                data-testid="notification-fire-log-rule"
              >
                {{ firing.ruleName }}
              </td>
              <td class="px-2 py-2 text-gray-600" data-testid="notification-fire-log-sink">
                {{ firing.sink }}
              </td>
              <td class="px-2 py-2">
                <span
                  class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium"
                  :class="outcomeClass(firing.outcome)"
                  data-testid="notification-fire-log-outcome"
                >
                  {{ firing.outcome }}
                </span>
              </td>
              <td
                class="px-2 py-2 text-gray-700 max-w-xs truncate"
                :title="firing.detail ?? ''"
                data-testid="notification-fire-log-detail"
              >
                {{ firing.detail ?? '' }}
              </td>
            </tr>
          </tbody>
        </table>
        <div
          class="px-3 py-2 border-t border-gray-200 flex items-center justify-between text-xs text-gray-500"
          data-testid="notification-fire-log-pager"
        >
          <span data-testid="notification-fire-log-range">{{ fireLogRange }}</span>
          <span class="flex items-center gap-1">
            <button
              type="button"
              class="px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              :disabled="!canGoPrev || fireLogLoading"
              data-testid="notification-fire-log-prev"
              @click="goPrev"
            >
              Prev
            </button>
            <button
              type="button"
              class="px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              :disabled="!canGoNext || fireLogLoading"
              data-testid="notification-fire-log-next"
              @click="goNext"
            >
              Next
            </button>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
