import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { $fetch } from 'ofetch'

export function useDslApi() {
  const log = useClientLogger('dsl')

  async function getDefinitions() {
    log.debug('fetching definitions')
    try {
      const result = await $fetch('/api/v1/dsl/definitions')
      const list = Array.isArray(result)
        ? result
        : ((result as { constructs?: unknown[] }).constructs ?? [])
      log.info('definitions loaded', { count: list.length })
      return result
    } catch (err) {
      log.error('failed to load definitions', { error: (err as Error).message })
      throw err
    }
  }

  async function searchObjects(
    filters: { name?: string; type?: string; description?: string } = {},
  ) {
    const query: Record<string, string> = {}
    if (filters.name?.trim()) query.name = filters.name.trim()
    if (filters.type?.trim()) query.type = filters.type.trim()
    if (filters.description?.trim()) query.description = filters.description.trim()

    log.debug('searching objects', { filters: query })
    return $fetch('/api/v1/dsl/objects/search', { query })
  }

  async function preview(name: string, body: unknown, metadata?: Record<string, unknown>) {
    log.info('preview request', { name })
    return $fetch(`/api/v1/dsl/preview/${name}`, {
      method: 'POST',
      body: { body, metadata },
    })
  }

  async function run(name: string, body: unknown, metadata?: Record<string, unknown>) {
    log.info('run request', { name })
    return $fetch(`/api/v1/dsl/run/${name}`, {
      method: 'POST',
      body: { body, metadata },
    })
  }

  async function explain(name: string, body: unknown, metadata?: Record<string, unknown>) {
    log.info('explain request', { name })
    return $fetch(`/api/v1/dsl/explain/${name}`, {
      method: 'POST',
      body: { body, metadata },
    })
  }

  async function saveDraft(
    name: string,
    payload: { name: string; type?: string; status?: string; version?: string; taskQueue?: string },
  ) {
    log.info('saveDraft request', { name })
    return $fetch(`/api/v1/dsl/drafts/${name}/save`, { method: 'POST', body: payload })
  }

  async function publishDraft(
    name: string,
    payload: { name: string; type?: string; status?: string; version?: string; taskQueue?: string },
  ) {
    log.info('publishDraft request', { name })
    return $fetch(`/api/v1/dsl/drafts/${name}/publish`, { method: 'POST', body: payload })
  }

  async function deleteDraft(name: string) {
    log.info('deleteDraft request', { name })
    return $fetch(`/api/v1/dsl/drafts/${name}/delete`, { method: 'DELETE' })
  }

  async function listDrafts() {
    log.info('listDrafts request')
    return $fetch('/api/v1/dsl/drafts')
  }

  async function readDraft(name: string) {
    log.info('readDraft request', { name })
    return $fetch(`/api/v1/dsl/drafts/${name}`)
  }

  async function validateConstruct(name: string) {
    // stub — calls preview to validate
    log.info('validate request', { name })
    return preview(name, {})
  }

  async function reload() {
    log.info('reload request')
    return $fetch('/api/v1/dsl/reload', { method: 'POST' })
  }

  async function getProcessDiagram(name: string, format: 'mermaid' | 'plantuml' | 'bpmn' = 'mermaid') {
    log.info('process diagram request', { name, format })
    return $fetch(`/api/v1/dsl/processes/${name}/diagram`, { query: { format } })
  }

  return {
    getDefinitions,
    searchObjects,
    preview,
    run,
    explain,
    saveDraft,
    publishDraft,
    deleteDraft,
    listDrafts,
    readDraft,
    validateConstruct,
    reload,
    getProcessDiagram,
  }
}
