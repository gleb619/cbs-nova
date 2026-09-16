import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import {
  type DefinitionTestCase,
  type DefinitionTestRunReport,
  type DiagnosticsPage,
  type DomainEventPage,
  type DomainEventQuery,
  type WebhookDeliveryPage,
  type WebhookDeliveryQuery,
  unwrapList,
} from '@cbs/components'
import { $fetch } from 'ofetch'
import { extractApiError } from '../utils/extractApiError'

export function useDslApi() {
  const log = useClientLogger('dsl')

  async function getDefinitions() {
    log.debug('fetching definitions')
    try {
      const result = await $fetch('/api/v1/dsl/definitions')
      const list = unwrapList(result)
      log.info('definitions loaded', { count: list.length })
      return result
    } catch (err) {
      log.error('failed to load definitions', { error: extractApiError(err).message })
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
      body: bundle as any,
      query,
    })
  }

  async function listDrafts(): Promise<unknown[]> {
    log.info('listDrafts request')
    const result = await $fetch('/api/v1/dsl/drafts')
    return unwrapList(result)
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

  async function getHistoryEntry(name: string, timestamp: string) {
    log.info('getHistoryEntry request', { name, timestamp })
    return $fetch(`/api/v1/dsl/drafts/${name}/history/${timestamp}`)
  }

  async function getHistoryDiff(name: string, timestamp: string) {
    log.info('getHistoryDiff request', { name, timestamp })
    return $fetch(`/api/v1/dsl/drafts/${name}/history/${timestamp}/diff`)
  }

  async function fetchDiagnostics(params: {
    definition?: string
    limit: number
    offset: number
  }): Promise<DiagnosticsPage> {
    log.info('fetchDiagnostics request', {
      definition: params.definition,
      limit: params.limit,
      offset: params.offset,
    })
    const query: Record<string, string> = {
      limit: String(params.limit),
      offset: String(params.offset),
    }
    if (params.definition?.trim()) query.definition = params.definition.trim()
    try {
      return (await $fetch('/api/v1/dsl/diagnostics', { query })) as DiagnosticsPage
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to load diagnostics', { error: message })
      throw new Error(message)
    }
  }

  async function fetchEvents(params: DomainEventQuery): Promise<DomainEventPage> {
    log.info('fetchEvents request', { ...params })
    const query: Record<string, string> = {
      limit: String(params.limit),
      offset: String(params.offset),
    }
    if (params.type?.trim()) query.type = params.type.trim()
    if (params.aggregateType?.trim()) query.aggregateType = params.aggregateType.trim()
    if (params.aggregateId?.trim()) query.aggregateId = params.aggregateId.trim()
    if (params.correlationId?.trim()) query.correlationId = params.correlationId.trim()
    if (params.since?.trim()) query.since = params.since.trim()
    try {
      return (await $fetch('/api/v1/dsl/events', { query })) as DomainEventPage
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to load domain events', { error: message })
      throw new Error(message)
    }
  }

  async function fetchWebhookDeliveries(
    params: WebhookDeliveryQuery,
  ): Promise<WebhookDeliveryPage> {
    log.info('fetchWebhookDeliveries request', { ...params })
    const query: Record<string, string> = {
      limit: String(params.limit),
      offset: String(params.offset),
    }
    if (params.subscriptionId?.trim()) query.subscriptionId = params.subscriptionId.trim()
    try {
      return (await $fetch('/api/v1/dsl/webhooks/deliveries', { query })) as WebhookDeliveryPage
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to load webhook deliveries', { error: message })
      throw new Error(message)
    }
  }

  async function fetchDefinitionTests(name: string): Promise<DefinitionTestCase[]> {
    log.info('fetchDefinitionTests request', { name })
    try {
      const result = await $fetch(`/api/v1/dsl/definitions/${name}/tests`)
      return unwrapList(result) as DefinitionTestCase[]
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to load definition tests', { error: message })
      throw new Error(message)
    }
  }

  async function saveDefinitionTests(name: string, cases: DefinitionTestCase[]): Promise<unknown> {
    log.info('saveDefinitionTests request', { name, count: cases.length })
    try {
      return await $fetch(`/api/v1/dsl/definitions/${name}/tests`, {
        method: 'PUT',
        // ofetch's body union excludes arrays; the backend expects the raw
        // JSON array of cases, so cast past the request-body typing.
        body: cases as unknown as Record<string, unknown>,
      })
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to save definition tests', { error: message })
      throw new Error(message)
    }
  }

  async function runDefinitionTests(
    name: string,
    caseNames?: string[],
  ): Promise<DefinitionTestRunReport> {
    log.info('runDefinitionTests request', { name, cases: caseNames?.length ?? 0 })
    try {
      const query = caseNames?.length ? { case: caseNames } : {}
      return (await $fetch(`/api/v1/dsl/definitions/${name}/tests/run`, {
        method: 'POST',
        query,
      })) as DefinitionTestRunReport
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to run definition tests', { error: message })
      throw new Error(message)
    }
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


  async function pauseSchedule(definition: string, reason?: string) {
    log.info('pauseSchedule request', { definition })
    return $fetch(`/api/v1/dsl/schedules/${definition}/pause`, {
      method: 'POST',
      body: reason ? { reason } : undefined,
    })
  }

  async function resumeSchedule(definition: string, reason?: string) {
    log.info('resumeSchedule request', { definition })
    return $fetch(`/api/v1/dsl/schedules/${definition}/resume`, {
      method: 'POST',
      body: reason ? { reason } : undefined,
    })
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
    listDrafts,
    readDraft,
    listPublishHistory,
    restorePublishHistory,
    getHistoryEntry,
    getHistoryDiff,
    fetchEvents,
    fetchDefinitionTests,
    saveDefinitionTests,
    runDefinitionTests,
    fetchDiagnostics,
    fetchWebhookDeliveries,
    validateConstruct,
    reload,
    listSchedules,
    createSchedule,
    deleteSchedule,
    pauseSchedule,
    resumeSchedule,
    getProcessDiagram,
  }
}
