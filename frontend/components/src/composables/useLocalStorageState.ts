import { type Ref, ref, watch } from 'vue'

export interface UseLocalStorageStateOptions<T> {
  read?: (raw: string | null) => T | undefined
  write?: (value: T) => string
}

export function useLocalStorageState<T>(
  key: string,
  defaultValue: T,
  options: UseLocalStorageStateOptions<T> = {},
): Ref<T> {
  const read =
    options.read ??
    ((raw: string | null): T | undefined => {
      if (raw === null) return undefined
      try {
        return JSON.parse(raw) as T
      } catch {
        return undefined
      }
    })
  const write = options.write ?? ((value: T) => JSON.stringify(value))

  const stored = typeof window !== 'undefined' ? window.localStorage.getItem(key) : null
  const parsed = read(stored)
  const state = ref<T>(parsed === undefined ? defaultValue : parsed) as Ref<T>

  watch(
    state,
    (value) => {
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(key, write(value))
      }
    },
    { deep: true },
  )

  return state
}
