<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { onSchedulesChanged } from '../../composables/useScheduleListEvents'
import type { CreateSchedulePayload, ScheduleSummary } from '../../types/dsl'

const props = defineProps<{
  schedules: ScheduleSummary[]
  loading?: boolean
  error?: string | null
  pausingDefinitions?: Record<string, boolean>
}>()

const emit = defineEmits<{
  create: [payload: CreateSchedulePayload]
  delete: [definition: string]
  pause: [definition: string]
  resume: [definition: string]
}>()

const definition = ref('')
const cron = ref('')
const timezone = ref('UTC')
const note = ref('')
const inputJson = ref('')
const inputError = ref('')

const pendingConfirm = ref<string | null>(null)
const confirmButtonRef = ref<HTMLButtonElement | null>(null)

onSchedulesChanged(() => {
  pendingConfirm.value = null
})

const canCreate = computed(() => definition.value.trim().length > 0 && cron.value.trim().length > 0)

function isPausing(definition: string) {
  return !!props.pausingDefinitions?.[definition]
}

function onTogglePause(schedule: ScheduleSummary) {
  if (schedule.paused) {
    emit('resume', schedule.definition)
  } else {
    emit('pause', schedule.definition)
  }
}

function resetForm() {
  definition.value = ''
  cron.value = ''
  timezone.value = 'UTC'
  note.value = ''
  inputJson.value = ''
  inputError.value = ''
}

function parseInput(): unknown | undefined {
  const raw = inputJson.value.trim()
  if (!raw) {
    return undefined
  }
  try {
    return JSON.parse(raw)
  } catch (e) {
    inputError.value = (e as Error).message
    return undefined
  }
}

function onCreate() {
  inputError.value = ''
  const input = parseInput()
  if (inputJson.value.trim().length > 0 && input === undefined) {
    return
  }
  emit('create', {
    definition: definition.value.trim(),
    cron: cron.value.trim(),
    timezone: timezone.value.trim(),
    input,
    note: note.value.trim() || undefined,
  })
  resetForm()
}

function onDelete(schedule: ScheduleSummary) {
  if (pendingConfirm.value === schedule.scheduleId) {
    return
  }
  pendingConfirm.value = schedule.scheduleId
  nextTick(() => {
    confirmButtonRef.value?.focus()
  })
}

function confirmDelete(schedule: ScheduleSummary) {
  emit('delete', schedule.definition)
  pendingConfirm.value = null
}

function cancelDelete() {
  pendingConfirm.value = null
}

function setConfirmButtonRef(scheduleId: string, el: HTMLButtonElement | null) {
  if (el && pendingConfirm.value === scheduleId) {
    confirmButtonRef.value = el
  } else if (pendingConfirm.value === scheduleId && confirmButtonRef.value === el) {
    confirmButtonRef.value = null
  }
}

function onPendingKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    cancelDelete()
  }
}
</script>

<template>
  <div
    data-testid="schedule-list"
    class="flex flex-col h-full bg-white rounded border border-gray-200 overflow-hidden"
  >
    <div class="px-4 py-3 border-b border-gray-200">
      <h2 class="font-semibold text-gray-900">Schedules</h2>
      <p class="text-xs text-gray-500 mt-1">
        Cron-driven Temporal schedules that start a published DSL workflow directly.
      </p>
    </div>

    <div class="flex-1 overflow-y-auto p-4 space-y-4">
      <form
        data-testid="schedule-create-form"
        class="space-y-3 p-3 rounded border border-gray-200 bg-gray-50"
        @submit.prevent="onCreate"
      >
        <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div class="flex flex-col gap-1">
            <label for="schedule-definition" class="text-xs font-medium text-gray-700"
              >Definition</label
            >
            <input
              id="schedule-definition"
              v-model="definition"
              data-testid="schedule-definition-input"
              type="text"
              placeholder="LoanDisbursement"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
          <div class="flex flex-col gap-1">
            <label for="schedule-cron" class="text-xs font-medium text-gray-700">Cron</label>
            <input
              id="schedule-cron"
              v-model="cron"
              data-testid="schedule-cron-input"
              type="text"
              placeholder="0 9 * * *"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
          <div class="flex flex-col gap-1">
            <label for="schedule-timezone" class="text-xs font-medium text-gray-700"
              >Timezone</label
            >
            <input
              id="schedule-timezone"
              v-model="timezone"
              data-testid="schedule-timezone-input"
              type="text"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
          <div class="flex flex-col gap-1">
            <label for="schedule-note" class="text-xs font-medium text-gray-700">Note</label>
            <input
              id="schedule-note"
              v-model="note"
              data-testid="schedule-note-input"
              type="text"
              class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500"
            >
          </div>
        </div>
        <div class="flex flex-col gap-1">
          <label for="schedule-input" class="text-xs font-medium text-gray-700"
            >Input JSON (optional)</label
          >
          <textarea
            id="schedule-input"
            v-model="inputJson"
            data-testid="schedule-input-textarea"
            rows="3"
            placeholder='{"amount": 100}'
            class="px-3 py-1.5 text-sm rounded border border-gray-300 focus:outline-none focus:border-blue-500 font-mono"
          ></textarea>
          <span v-if="inputError" data-testid="schedule-input-error" class="text-xs text-red-600"
            >{{ inputError }}</span
          >
        </div>
        <button
          type="submit"
          data-testid="schedule-create-submit"
          :disabled="!canCreate"
          class="px-3 py-1.5 text-sm rounded bg-blue-600 text-white disabled:opacity-50 disabled:cursor-not-allowed hover:bg-blue-700"
        >
          Create Schedule
        </button>
      </form>

      <div v-if="loading" class="space-y-3" data-testid="schedule-list-loading">
        <div v-for="i in 3" :key="i" class="h-16 bg-gray-100 rounded animate-pulse" />
      </div>

      <div v-else-if="error" class="text-sm text-red-600" data-testid="schedule-list-error">
        {{ error }}
      </div>

      <div
        v-else-if="schedules.length === 0"
        class="text-sm text-gray-500 italic text-center py-8"
        data-testid="schedule-list-empty"
      >
        No schedules configured.
      </div>

      <ul v-else class="space-y-2">
        <li
          v-for="schedule in schedules"
          :key="schedule.scheduleId"
          data-testid="schedule-row"
          class="px-3 py-2 rounded border border-gray-100 hover:bg-gray-50 flex items-center justify-between gap-3"
        >
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <span class="font-medium text-gray-900 truncate">{{ schedule.definition }}</span>
              <span
                v-if="schedule.paused"
                class="text-[10px] px-1.5 py-0.5 rounded-full bg-gray-100 text-gray-700"
              >
                paused
              </span>
            </div>
            <div class="text-xs text-gray-500 mt-0.5">
              {{ schedule.cron }}
              · {{ schedule.timezone }}
              <span v-if="schedule.nextRunAt">· next {{ schedule.nextRunAt }}</span>
            </div>
            <div v-if="schedule.note" class="text-xs text-gray-600 mt-0.5 truncate">
              {{ schedule.note }}
            </div>
          </div>
          <button
            type="button"
            data-testid="schedule-pause-toggle"
            :disabled="isPausing(schedule.definition)"
            class="p-1.5 rounded border border-gray-300 text-gray-700 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed shrink-0"
            :title="schedule.paused ? 'Resume schedule' : 'Pause schedule'"
            @click="onTogglePause(schedule)"
          >
            <svg
              v-if="!schedule.paused"
              class="w-4 h-4"
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
            >
              <rect x="6" y="4" width="4" height="16" rx="1" />
              <rect x="14" y="4" width="4" height="16" rx="1" />
            </svg>
            <svg
              v-else
              class="w-4 h-4"
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
            >
              <path d="M8 5v14l11-7z" />
            </svg>
          </button>
          <button
            v-if="pendingConfirm !== schedule.scheduleId"
            type="button"
            data-testid="schedule-delete"
            class="px-2 py-1 text-xs rounded border border-red-300 text-red-700 hover:bg-red-50 shrink-0"
            @click="onDelete(schedule)"
          >
            Delete
          </button>
          <fieldset
            v-else
            class="flex items-center gap-1 shrink-0 border-0 p-0 m-0"
            data-testid="schedule-delete-confirm-group"
            @keydown="onPendingKeydown"
          >
            <button
              :ref="(el) => setConfirmButtonRef(schedule.scheduleId, el as HTMLButtonElement | null)"
              type="button"
              data-testid="schedule-delete-confirm"
              class="px-2 py-1 text-xs rounded bg-red-600 text-white hover:bg-red-700"
              @click="confirmDelete(schedule)"
            >
              Confirm
            </button>
            <button
              type="button"
              data-testid="schedule-delete-cancel"
              class="px-2 py-1 text-xs rounded border border-gray-300 text-gray-700 hover:bg-gray-100"
              @click="cancelDelete"
            >
              Cancel
            </button>
          </fieldset>
        </li>
      </ul>
    </div>
  </div>
</template>
