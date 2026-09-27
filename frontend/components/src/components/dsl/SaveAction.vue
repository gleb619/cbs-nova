<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import HotkeyTooltip from '../HotkeyTooltip.vue'

export type SaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'error'

export interface AutosaveOption {
  value: string
  label: string
}

const props = withDefaults(
  defineProps<{
    isDirty: boolean
    busy?: boolean
    saveStatus?: SaveStatus
    lastSavedAt?: Date | null
    autosaveOptions?: AutosaveOption[]
    autosaveMode?: string
    saveShortcut?: string
    testidPrefix?: string
  }>(),
  {
    busy: false,
    saveStatus: 'idle',
    lastSavedAt: null,
    autosaveOptions: undefined,
    autosaveMode: '',
    saveShortcut: '',
    testidPrefix: 'save-action',
  },
)

const emit = defineEmits<{
  save: []
  'update:autosaveMode': [value: string]
}>()

const dropdownOpen = ref(false)
const rootRef = ref<HTMLElement | null>(null)

const hasAutosave = computed(
  () => !!props.autosaveOptions && props.autosaveOptions.length > 0,
)

function requestSave() {
  // Manual save is always available — user decides when to save.
  if (props.busy) return
  emit('save')
}

function toggleDropdown() {
  if (!hasAutosave.value) return
  dropdownOpen.value = !dropdownOpen.value
}

function closeDropdown() {
  dropdownOpen.value = false
}

function selectOption(opt: AutosaveOption) {
  emit('update:autosaveMode', opt.value)
  closeDropdown()
}

function onClickOutside(event: MouseEvent) {
  if (!dropdownOpen.value) return
  if (rootRef.value && !rootRef.value.contains(event.target as Node)) closeDropdown()
}

function onKeydown(event: KeyboardEvent) {
  if (!dropdownOpen.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    closeDropdown()
  }
}

onMounted(() => {
  window.addEventListener('mousedown', onClickOutside)
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('mousedown', onClickOutside)
  window.removeEventListener('keydown', onKeydown)
})

const saveStatusClasses = computed(() => {
  switch (props.saveStatus) {
    case 'dirty':
      return 'bg-amber-50 text-amber-700 border border-amber-200'
    case 'saving':
      return 'bg-blue-50 text-blue-700 border border-blue-200'
    case 'saved':
      return 'bg-green-50 text-green-700 border border-green-200'
    case 'error':
      return 'bg-red-50 text-red-700 border border-red-200'
    default:
      return 'text-gray-500'
  }
})

function formatSavedTime(date: Date | null): string {
  if (!date) return ''
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000)
  if (seconds < 10) return 'just now'
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  return `${Math.floor(minutes / 60)}h ago`
}

const saveStatusText = computed(() => {
  switch (props.saveStatus) {
    case 'dirty':
      return 'Unsaved changes'
    case 'saving':
      return 'Saving…'
    case 'saved':
      return `Saved ${formatSavedTime(props.lastSavedAt ?? null)}`
    case 'error':
      return 'Save failed — Retry'
    default:
      return props.lastSavedAt ? 'Saved' : ''
  }
})
</script>

<template>
  <div
    ref="rootRef"
    class="relative inline-flex items-center gap-2"
    :data-testid="`${testidPrefix}-save-root`"
  >
    <span
      v-if="saveStatusText"
      class="inline-flex items-center gap-1.5 text-xs rounded-full px-2.5 py-1"
      :class="saveStatusClasses"
      :data-testid="`${testidPrefix}-save-status`"
      role="status"
    >
      <span
        v-if="saveStatus === 'saving'"
        class="inline-block h-3 w-3 rounded-full border-2 border-current border-t-transparent animate-spin"
        aria-hidden="true"
      ></span>
      <span>{{ saveStatusText }}</span>
      <button
        v-if="saveStatus === 'error'"
        type="button"
        class="ml-1 underline hover:no-underline"
        :data-testid="`${testidPrefix}-save-retry`"
        @click="requestSave"
      >
        Retry
      </button>
    </span>
    <span
      v-if="isDirty"
      class="inline-flex items-center gap-1.5 text-xs text-amber-700"
      :data-testid="`${testidPrefix}-dirty-indicator`"
      role="status"
      aria-label="You have unsaved changes"
    >
      <span aria-hidden="true" class="inline-block h-2 w-2 rounded-full bg-amber-500"></span>
      <span>unsaved</span>
    </span>
    <div
      class="inline-flex rounded overflow-hidden border border-blue-600"
      :data-testid="`${testidPrefix}-save-split`"
    >
      <HotkeyTooltip :keys="saveShortcut">
        <button
          type="button"
          class="px-3 py-1 text-xs font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed"
          :disabled="busy"
          :data-testid="`${testidPrefix}-save`"
          @click="requestSave"
        >
          Save
        </button>
      </HotkeyTooltip>
      <span v-if="hasAutosave" class="w-px bg-blue-700" aria-hidden="true"></span>
      <button
        v-if="hasAutosave"
        type="button"
        class="px-2 py-1 text-xs inline-flex items-center justify-center text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed"
        :disabled="busy"
        :aria-haspopup="'menu'"
        :aria-expanded="dropdownOpen ? 'true' : 'false'"
        :data-testid="`${testidPrefix}-save-dropdown-toggle`"
        @click="toggleDropdown"
      >
        <span aria-hidden="true">▾</span>
      </button>
    </div>
    <div
      v-if="dropdownOpen && hasAutosave"
      class="absolute right-0 top-full mt-1 z-50 min-w-[10rem] bg-white border border-neutral-200 rounded shadow-lg py-1"
      :data-testid="`${testidPrefix}-save-dropdown`"
      role="menu"
    >
      <button
        v-for="opt in autosaveOptions"
        :key="opt.value"
        type="button"
        role="menuitem"
        class="w-full text-left px-3 py-1.5 text-xs flex items-center justify-between hover:bg-neutral-100"
        :class="autosaveMode === opt.value ? 'font-semibold text-blue-700' : 'text-neutral-700'"
        :data-testid="`${testidPrefix}-autosave-${opt.value}`"
        @click="selectOption(opt)"
      >
        <span>{{ opt.label }}</span>
        <span v-if="autosaveMode === opt.value" aria-hidden="true">✓</span>
      </button>
    </div>
  </div>
</template>
