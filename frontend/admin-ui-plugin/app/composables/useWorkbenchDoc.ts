import { useCrc32 } from '@cbs/components'
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

function defaultDocTemplate(name: string): string {
  return `---\nname: ${name}\ndescription: \n---\n`
}

export function useWorkbenchDoc(options: UseWorkbenchDocOptions): UseWorkbenchDocReturn {
  const { calculateCrc32 } = useCrc32()

  const doc = ref('')
  const savedHash = ref<number | null>(null)
  const saveStatus = ref<DocSaveStatus>('idle')
  const lastSavedAt = ref<Date | null>(null)
  let loadToken = 0

  const dirty = computed(
    () => savedHash.value !== null && calculateCrc32(doc.value) !== savedHash.value,
  )

  async function loadDoc(name: string): Promise<void> {
    const token = ++loadToken
    if (!name) {
      doc.value = ''
      savedHash.value = null
      saveStatus.value = 'idle'
      return
    }
    let content: string | null = null
    try {
      content = await options.readDoc(name)
    } catch (err) {
      options.logError?.('failed to load doc', { error: (err as Error).message })
    }
    if (token !== loadToken) return
    doc.value = content && content.length > 0 ? content : defaultDocTemplate(name)
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
      lastSavedAt.value = new Date()
      saveStatus.value = 'saved'
    } catch (err) {
      options.logError?.('failed to save doc', { error: (err as Error).message })
      saveStatus.value = 'error'
    }
  }

  let currentName = ''

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
