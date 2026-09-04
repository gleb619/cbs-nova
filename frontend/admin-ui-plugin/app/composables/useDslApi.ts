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

  async function run(
    name: string,
    body: unknown,
    metadata?: Record<string, unknown>,
    headers?: Record<string, string>,
  ) {
    log.info('run request', { name })
    return $fetch(`/api/v1/dsl/run/${name}`, {
      method: 'POST',
      body: { body, metadata },
      ...(headers ? { headers } : {}),
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
    payload: {
      name: string
      type?: string
      status?: string
      version?: string
      taskQueue?: string
      description?: string
    },
  ) {
    log.info('saveDraft request', { name })
    return $fetch(`/api/v1/dsl/drafts/${name}/save`, { method: 'POST', body: payload })
  }

  async function publishDraft(
    name: string,
    payload: {
      name: string
      type?: string
      status?: string
      version?: string
      taskQueue?: string
      description?: string
    },
  ) {
    log.info('publishDraft request', { name })
    return $fetch(`/api/v1/dsl/drafts/${name}/publish`, { method: 'POST', body: payload })
  }

  async function deleteDraft(name: string) {
    log.info('deleteDraft request', { name })
    return $fetch(`/api/v1/dsl/drafts/${name}/delete`, { method: 'DELETE' })
  }

  async function readDslFile(name: string) {
    log.info('readDslFile request', { name })
    const result = await $fetch(`/api/v1/dsl/files/by-name/${name}`)
    return (result as { content?: string }).content ?? ''
  }

  async function writeDslFile(name: string, content: string) {
    log.info('writeDslFile request', { name })
    return $fetch(`/api/v1/dsl/files/by-name/${name}`, {
      method: 'POST',
      body: { content },
    })
  }

  async function updateDescription(name: string, description: string) {
    log.info('updateDescription request', { name })
    return $fetch(`/api/v1/dsl/definitions/${name}/description`, {
      method: 'PATCH',
      body: { description },
    })
  }

  async function listHelpers() {
    log.info('listHelpers request')
    return $fetch('/api/v1/dsl/helpers')
  }

  async function exportDefinitions(includeDrafts?: boolean) {
    log.info('exportDefinitions request', { includeDrafts })
    const query = includeDrafts ? { include: 'drafts' } : {}
    return $fetch('/api/v1/dsl/definitions/export', { query })
  }

  async function importDefinitions(bundle: unknown, dryRun?: boolean) {
    log.info('importDefinitions request', { dryRun })
    const query = dryRun ? { dryRun: 'true' } : {}
    return $fetch('/api/v1/dsl/definitions/import', {
      method: 'POST',
      body: bundle,
      query,
    })
  }

  async function listDrafts() {
    log.info('listDrafts request')
    return $fetch('/api/v1/dsl/drafts')
  }

  async function readDraft(name: string) {
    log.info('readDraft request', { name })
    return $fetch(`/api/v1/dsl/drafts/${name}`)
  }

  async function listPublishHistory(name: string) {
    log.info('listPublishHistory request', { name })
    return $fetch(`/api/v1/dsl/drafts/${name}/history`)
  }

  async function restorePublishHistory(name: string, timestamp: string) {
    log.info('restorePublishHistory request', { name, timestamp })
    return $fetch(`/api/v1/dsl/drafts/${name}/history/${timestamp}/restore`, { method: 'POST' })
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

  async function listSchedules() {
    log.info('listSchedules request')
    return $fetch('/api/v1/dsl/schedules')
  }

  async function createSchedule(payload: Record<string, unknown>) {
    log.info('createSchedule request', { definition: payload.definition })
    return $fetch('/api/v1/dsl/schedules', { method: 'POST', body: payload })
  }

  async function deleteSchedule(definition: string) {
    log.info('deleteSchedule request', { definition })
    return $fetch(`/api/v1/dsl/schedules/${definition}`, { method: 'DELETE' })
  }

  async function getProcessDiagram(
    name: string,
    format: 'mermaid' | 'plantuml' | 'bpmn' = 'mermaid',
  ) {
    log.info('process diagram request', { name, format })
    return $fetch(`/api/v1/dsl/processes/${name}/diagram`, { query: { format } })
  }

  return {
    getDefinitions,
    listHelpers,
    exportDefinitions,
    importDefinitions,
    searchObjects,
    preview,
    run,
    explain,
    saveDraft,
    publishDraft,
    deleteDraft,
    readDslFile,
    writeDslFile,
    updateDescription,
    listDrafts,
    readDraft,
    listPublishHistory,
    restorePublishHistory,
    validateConstruct,
    reload,
    listSchedules,
    createSchedule,
    deleteSchedule,
    getProcessDiagram,
  }
}
