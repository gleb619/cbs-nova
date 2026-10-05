import type { ComputedRef, Ref } from 'vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'nuxt/app'

export const EXECUTION_DETAIL_TABS = [
  'payload',
  'metadata',
  'logs',
  'errors',
  'transactions',
] as const
export type ExecutionDetailTab = (typeof EXECUTION_DETAIL_TABS)[number]

type QueryValue = string | string[] | null | undefined

export interface UseExecutionDeepLinkOptions {
  availableTabs: Ref<readonly ExecutionDetailTab[]> | ComputedRef<readonly ExecutionDetailTab[]>
}

export interface UseExecutionDeepLinkReturn {
  activeTab: Ref<ExecutionDetailTab>
  visibleTab: ComputedRef<ExecutionDetailTab>
}

/**
 * Syncs the execution detail page's active tab with the URL query `?activeTab=`.
 *
 * - On mount, the query param drives `activeTab`.
 * - Invalid or missing values fall back to `payload`.
 * - `visibleTab` falls back to `payload` when the chosen tab is not in
 *   `availableTabs`, so deep-links never force-show unavailable tabs.
 * - Tab selection writes back to the URL with `router.replace` (no history spam).
 * - Route query changes (e.g. back/forward) restore the matching tab.
 */
export function useExecutionDeepLink(
  options: UseExecutionDeepLinkOptions,
): UseExecutionDeepLinkReturn {
  const route = useRoute()
  const router = useRouter()
  const { availableTabs } = options

  const activeTab = ref<ExecutionDetailTab>(parseTabValue(route.query.activeTab))

  const visibleTab = computed<ExecutionDetailTab>(() =>
    availableTabs.value.includes(activeTab.value) ? activeTab.value : 'payload',
  )

  watch(
    () => route.query.activeTab,
    (raw) => {
      activeTab.value = parseTabValue(raw)
    },
  )

  watch(activeTab, (tab) => {
    if (route.query.activeTab === tab) return
    void router.replace({ query: { ...route.query, activeTab: tab } })
  })

  return { activeTab, visibleTab }
}

function firstQueryValue(value: QueryValue): string | undefined {
  if (value == null) return undefined
  if (Array.isArray(value)) {
    const head = value[0]
    return head ?? undefined
  }
  return value
}

function parseTabValue(raw: QueryValue): ExecutionDetailTab {
  const candidate = firstQueryValue(raw)
  if (candidate && (EXECUTION_DETAIL_TABS as readonly string[]).includes(candidate)) {
    return candidate as ExecutionDetailTab
  }
  return 'payload'
}
