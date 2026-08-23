import { type Ref, ref } from 'vue'

export interface UseLoaderStateOptions {
  namespace?: string
}

export interface LoaderStateRef extends Ref<boolean> {
  start: () => void
  stop: () => void
}

const loaderStore = new Map<string, LoaderStateRef>()

function buildKey(key: string, namespace?: string): string {
  return namespace ? `${namespace}:${key}` : `__global__:${key}`
}

function createLoaderRef(): LoaderStateRef {
  const r = ref(false) as LoaderStateRef
  r.start = () => {
    r.value = true
  }
  r.stop = () => {
    r.value = false
  }
  return r
}

export function useLoaderState(key: string, options: UseLoaderStateOptions = {}): LoaderStateRef {
  const fullKey = buildKey(key, options.namespace)

  let loader = loaderStore.get(fullKey)
  if (!loader) {
    loader = createLoaderRef()
    loaderStore.set(fullKey, loader)
  }

  return loader
}

export function createNamespacedLoaderState(namespace: string): (key: string) => LoaderStateRef {
  return function useNamespacedLoaderState(key: string): LoaderStateRef {
    return useLoaderState(key, { namespace })
  }
}
