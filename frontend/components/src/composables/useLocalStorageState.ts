import { type Ref, ref, watch } from 'vue'

export interface UseLocalStorageStateOptions<T> {
  namespace?: string
  read?: (raw: string | null) => T | undefined
  write?: (value: T) => string
}

function buildStorageKey(key: string, namespace?: string): string {
  if (!namespace) return key
  return `${namespace}:${key}`
}

export function useLocalStorageState<T>(
  key: string,
  defaultValue: T,
  options: UseLocalStorageStateOptions<T> = {},
): Ref<T> {
  const { namespace, read, write } = options
  const storageKey = buildStorageKey(key, namespace)

  const readValue =
    read ??
    ((raw: string | null): T | undefined => {
      if (raw === null) return undefined
      try {
        return JSON.parse(raw) as T
      } catch {
        return undefined
      }
    })

  const writeValue = write ?? ((value: T) => JSON.stringify(value))

  const stored = typeof window !== 'undefined' ? window.localStorage.getItem(storageKey) : null
  const parsed = readValue(stored)
  const state = ref<T>(parsed === undefined ? defaultValue : parsed) as Ref<T>

  watch(
    state,
    (value) => {
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(storageKey, writeValue(value))
      }
    },
    { deep: true },
  )

  return state
}

export function createNamespacedLocalStorageState(namespace: string) {
  return function useNamespacedLocalStorageState<T>(
    key: string,
    defaultValue: T,
    options: Omit<UseLocalStorageStateOptions<T>, 'namespace'> = {},
  ): Ref<T> {
    return useLocalStorageState<T>(key, defaultValue, { ...options, namespace })
  }
}
