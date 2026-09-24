import type { LocationQuery, LocationQueryValue } from 'vue-router'
import { BODY_EDITOR_TABS, type BodyEditorTab } from './useWorkbenchShareLink'

export interface UseWorkbenchDeepLinkOptions {
  /** Reads the current route query (`useRoute().query`). */
  routeQuery: LocationQuery
  /** Apply the `?objectName=` value to the explorer filter. */
  setExplorerFilter: (value: string) => void
  /** Select the construct after the working set has loaded. */
  safeSelectConstruct: (name: string) => void
  /** Select a draft named in the `?draft=` param. */
  selectConstruct: (name: string) => void
}

export interface UseWorkbenchDeepLinkReturn {
  /** Runs after `loadConstructs()` finishes; selects `?objectName=` if present. */
  applyObjectName: () => void
  /** Emits the `cbs:body-editor:set-tab` window event for `?activeTab=`. */
  applyActiveTab: () => void
  /**
   * Applies `?draft=<name>` from the route query. Returns `true` only when a
   * non-empty name was present (so the caller can decide whether to follow
   * up with side effects like `syncSelectionEffects`).
   */
  applyDraft: () => boolean
  /** One-shot run for `?objectName=` deep linking. */
  resolveDeepLink: () => void
}

/**
 * Parses the workbench deep-link query params on mount.
 *
 * Decision: extracted from `dsl-workbench.vue` so the page stays a thin
 * orchestrator. Behaviour is identical to the inline version: `?objectName=`
 * seeds the explorer filter + selects the matching construct after the
 * working set loads; `?activeTab=` dispatches a `cbs:body-editor:set-tab`
 * window event the body editor listens for; `?draft=` selects the named
 * construct via the navbar widget's pick path. URL helpers (query parsing +
 * `consumeObjectNameQuery`) belong here because they are pure URL logic.
 */
export function useWorkbenchDeepLink(
  options: UseWorkbenchDeepLinkOptions,
): UseWorkbenchDeepLinkReturn {
  const { routeQuery, setExplorerFilter, safeSelectConstruct, selectConstruct } = options

  function firstQueryValue(value: LocationQueryValue | LocationQueryValue[] | undefined) {
    if (Array.isArray(value)) {
      const head = value[0]
      return head == null ? undefined : head
    }
    return value ?? undefined
  }

  function applyObjectName() {
    const objectName = firstQueryValue(routeQuery.objectName)
    if (objectName) {
      setExplorerFilter(objectName)
    }
  }

  function applyActiveTab() {
    const tabCandidate = firstQueryValue(routeQuery.activeTab)
    if (
      typeof tabCandidate === 'string' &&
      (BODY_EDITOR_TABS as readonly string[]).includes(tabCandidate) &&
      typeof window !== 'undefined'
    ) {
      window.dispatchEvent(
        new CustomEvent<BodyEditorTab>('cbs:body-editor:set-tab', {
          detail: tabCandidate as BodyEditorTab,
        }),
      )
    }
  }

  function applyDraft(): boolean {
    const requestedName = firstQueryValue(routeQuery.draft)
    if (requestedName) {
      selectConstruct(requestedName)
      return true
    }
    return false
  }

  // Strip the `?objectName=` param once we have consumed it. Keeps reloads
  // from re-overriding the user's persisted selection.
  function consumeObjectNameQuery() {
    if (typeof window === 'undefined') return
    const url = new URL(window.location.href)
    if (!url.searchParams.has('objectName')) return
    url.searchParams.delete('objectName')
    window.history.replaceState({}, '', url.toString())
  }

  function resolveDeepLink() {
    const objectName = firstQueryValue(routeQuery.objectName)
    if (objectName) {
      safeSelectConstruct(objectName)
      consumeObjectNameQuery()
    }
  }

  return {
    applyObjectName,
    applyActiveTab,
    applyDraft,
    resolveDeepLink,
  }
}
