import { type ComputedRef, computed, type Ref } from 'vue'
import type { PreviewHistoryEntry } from '../types/runner'
import { useLocalStorageState } from './useLocalStorageState'

export const PREVIEW_HISTORY_LIMIT = 20
export const PREVIEW_HISTORY_STORAGE_NAMESPACE = 'cbs-nova:preview'
export const PREVIEW_HISTORY_STORAGE_KEY = 'history'
const MAX_ENTRIES_PER_NAME = PREVIEW_HISTORY_LIMIT * 20

export interface PreviewHistory {
  entries: ComputedRef<PreviewHistoryEntry[]>
  record: (entry: Omit<PreviewHistoryEntry, 'id' | 'startedAt'>) => PreviewHistoryEntry
  remove: (id: string) => void
  clear: () => void
}

let sharedEntries: Ref<PreviewHistoryEntry[]> | null = null

export function __resetPreviewHistoryForTests() {
  sharedEntries = null
}

function entriesRef(): Ref<PreviewHistoryEntry[]> {
  sharedEntries ??= useLocalStorageState<PreviewHistoryEntry[]>(PREVIEW_HISTORY_STORAGE_KEY, [], {
    namespace: PREVIEW_HISTORY_STORAGE_NAMESPACE,
  })
  return sharedEntries
}

export function usePreviewHistory(
  name: () => string,
  options: { limit?: number } = {},
): PreviewHistory {
  const limit = Math.max(1, options.limit ?? PREVIEW_HISTORY_LIMIT)
  const all = entriesRef()

  const entries = computed(() => all.value.filter((entry) => entry.name === name()))

  function record(input: Omit<PreviewHistoryEntry, 'id' | 'startedAt'>): PreviewHistoryEntry {
    const entry: PreviewHistoryEntry = {
      ...input,
      id: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
      startedAt: new Date().toISOString(),
    }
    const sameName = all.value.filter((e) => e.name === entry.name).slice(0, limit - 1)
    const otherNames = all.value.filter((e) => e.name !== entry.name).slice(0, MAX_ENTRIES_PER_NAME)
    all.value = [entry, ...sameName, ...otherNames]
    return entry
  }

  function remove(id: string) {
    all.value = all.value.filter((entry) => entry.id !== id)
  }

  function clear() {
    all.value = all.value.filter((entry) => entry.name !== name())
  }

  return { entries, record, remove, clear }
}
