import { useRuntimeConfig } from 'nuxt/app'

/**
 * T302 — builds deep-links to the Temporal Web UI for a given workflow id.
 *
 * The feature is opt-in via the `temporalUiBaseUrl` runtime config key. When
 * the base URL is blank, `workflowUrl` always returns `null` so the caller can
 * cheaply guard the rendered link with `v-if`.
 */
export function useTemporalLink() {
  const config = useRuntimeConfig().public
  const baseUrl = String(config.temporalUiBaseUrl ?? '')
  const namespace = String(config.temporalNamespace ?? 'default')

  const enabled = baseUrl.trim().length > 0

  function workflowUrl(workflowId: string | null | undefined): string | null {
    if (!enabled) return null
    if (!workflowId) return null
    const trimmed = workflowId.trim()
    if (trimmed.length === 0) return null
    const base = baseUrl.replace(/\/$/, '')
    return `${base}/namespaces/${encodeURIComponent(namespace)}/workflows/${encodeURIComponent(trimmed)}`
  }

  return { enabled, workflowUrl }
}