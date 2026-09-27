import { createDefaultMarkdownDoc, useCrc32 } from '@cbs/components'
import { type ComputedRef, computed, ref } from 'vue'

export type DocSaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'error'

export interface UseWorkbenchDocOptions {
  readDoc: (name: string) => Promise<string | null>
  writeDoc: (name: string, content: string) => Promise<unknown>
  logError?: (message: string, fields?: Record<string, unknown>) => void
}

export interface UseWorkbenchDocReturn {
  doc: Ref<string>
  dirty: ComputedRef<boolean>
  saveStatus: Ref<DocSaveStatus>
  lastSavedAt: Ref<Date | null>
  loadDoc: (name: string) => Promise<void>
  updateDoc: (value: string) => void
  saveDoc: () => Promise<void>
}

export function useWorkbenchDoc(options: UseWorkbenchDocOptions): UseWorkbenchDocReturn {
  const { calculateCrc32 } = useCrc32()

  const doc = ref('')
  const savedHash = ref<number | null>(null)
  const saveStatus = ref<DocSaveStatus>('idle')
  const lastSavedAt = ref<Date | null>(null)
  // False while the doc is only a seeded template that has never been written
  // to disk, so the first Save creates the file even when nothing was edited.
  const persisted = ref(false)
  let loadToken = 0
  let currentName = ''

  const dirty = computed(
    () =>
      currentName !== '' &&
      (persisted.value
        ? savedHash.value !== null && calculateCrc32(doc.value) !== savedHash.value
        : true),
  )

  async function loadDoc(name: string): Promise<void> {
    const token = ++loadToken
    if (!name) {
      doc.value = ''
      savedHash.value = null
      saveStatus.value = 'idle'
      persisted.value = false
      return
    }
    let content: string | null = null
    try {
      content = await options.readDoc(name)
    } catch (err) {
      options.logError?.('failed to load doc', { error: (err as Error).message })
    }
    if (token !== loadToken) return
    persisted.value = Boolean(content && content.length > 0)
    doc.value = persisted.value ? (content as string) : createDefaultMarkdownDoc(name)
    savedHash.value = calculateCrc32(doc.value)
    saveStatus.value = 'idle'
  }

  function updateDoc(value: string): void {
    doc.value = value
    if (saveStatus.value === 'saved' || saveStatus.value === 'error') {
      saveStatus.value = 'idle'
    }
  }

  async function saveDoc(): Promise<void> {
    const name = currentName
    if (!name || !dirty.value) return
    saveStatus.value = 'saving'
    try {
      await options.writeDoc(name, doc.value)
      savedHash.value = calculateCrc32(doc.value)
      persisted.value = true
      lastSavedAt.value = new Date()
      saveStatus.value = 'saved'
    } catch (err) {
      options.logError?.('failed to save doc', { error: (err as Error).message })
      saveStatus.value = 'error'
    }
  }

  return {
    doc,
    dirty,
    saveStatus,
    lastSavedAt,
    loadDoc: async (name: string) => {
      currentName = name
      await loadDoc(name)
    },
    updateDoc,
    saveDoc,
  }
}
