import { customRef, getCurrentInstance, onMounted, type Ref } from 'vue'

export type UseCookieFactory = <T = unknown>(
  name: string,
  options?: { default?: () => T },
) => Ref<T>

export interface UseLocalStorageStateOptions<T> {
  namespace?: string
  read?: (raw: string | null) => T | undefined
  write?: (value: T) => string
  useCookie?: UseCookieFactory
}

function buildStorageKey(key: string, namespace?: string): string {
  if (!namespace) return key
  return `${namespace}:${key}`
}

function isObject(value: unknown): value is Record<PropertyKey, unknown> {
  return typeof value === 'object' && value !== null
}

export function useLocalStorageState<T>(
  key: string,
  defaultValue: T,
  options: UseLocalStorageStateOptions<T> = {},
): Ref<T> {
  const { namespace, read, write, useCookie } = options
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

  const cookieRef = useCookie
    ? useCookie<T>(storageKey, { default: () => defaultValue })
    : undefined

  const localValue =
    typeof window !== 'undefined' ? readValue(window.localStorage.getItem(storageKey)) : undefined

  const initialValue = cookieRef ? cookieRef.value : (localValue ?? defaultValue)

  let current: T = initialValue
  let triggerChange: (() => void) | null = null
  const proxyCache = new WeakMap<object, unknown>()

  function persist(value: T): void {
    if (typeof window === 'undefined') return
    window.localStorage.setItem(storageKey, writeValue(value))
    if (cookieRef && cookieRef.value !== value) {
      cookieRef.value = value
    }
  }

  function wrap<V>(value: V): V {
    if (!isObject(value)) return value
    const cached = proxyCache.get(value)
    if (cached !== undefined) return cached as V
    const proxy = new Proxy(value, {
      get(target, prop, receiver) {
        return wrap(Reflect.get(target, prop, receiver))
      },
      set(target, prop, next, receiver) {
        const ok = Reflect.set(target, prop, next, receiver)
        persist(current)
        triggerChange?.()
        return ok
      },
      deleteProperty(target, prop) {
        const ok = Reflect.deleteProperty(target, prop)
        persist(current)
        triggerChange?.()
        return ok
      },
    })
    proxyCache.set(value, proxy)
    return proxy as V
  }

  const state = customRef<T>((track, trigger) => {
    triggerChange = trigger
    return {
      get() {
        track()
        return wrap(current)
      },
      set(value) {
        current = value
        persist(value)
        trigger()
      },
    }
  }) as Ref<T>

  if (typeof window !== 'undefined' && cookieRef) {
    // Re-read localStorage after hydration: it can be newer than the cookie
    // (cookie expiry, size limits). Only safe inside a component, where the
    // update lands after hydration — mutating earlier would desync the
    // server-rendered markup, so outside a component the cookie stays
    // authoritative.
    if (getCurrentInstance()) {
      onMounted(() => {
        const stored = readValue(window.localStorage.getItem(storageKey))
        if (stored !== undefined && stored !== state.value) {
          state.value = stored
        }
        if (cookieRef.value !== state.value) {
          cookieRef.value = state.value
        }
      })
    }
  }

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
