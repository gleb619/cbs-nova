import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import type { DefinitionHistoryEntry, HistoryDiffResponse } from '../components/DslHistoryPanel.vue'
import {
  type DefinitionTestCase,
  type DefinitionTestRunReport,
  type DiagnosticsPage,
  type DomainEventPage,
  type DomainEventQuery,
  unwrapList,
  type WebhookDeliveryPage,
  type WebhookDeliveryQuery,
} from '@cbs/components'
import { $fetch } from 'ofetch'
import {
  createSchedule as bffCreateSchedule,
  deleteDraft as bffDeleteDraft,
  deleteSchedule as bffDeleteSchedule,
  diffPublishHistoryEntry as bffDiffPublishHistoryEntry,
  explainDsl as bffExplainDsl,
  exportDefinitions as bffExportDefinitions,
  importDefinitions as bffImportDefinitions,
  getProcessDiagram as bffGetProcessDiagram,
  listCompileDiagnostics as bffListCompileDiagnostics,
  listDefinitionTests as bffListDefinitionTests,
  listDefinitions as bffListDefinitions,
  listDomainEvents as bffListDomainEvents,
  listDrafts as bffListDrafts,
  listHelpers as bffListHelpers,
  listPublishHistory as bffListPublishHistory,
  listSchedules as bffListSchedules,
  pauseSchedule as bffPauseSchedule,
  previewDsl as bffPreviewDsl,
  publishDraft as bffPublishDraft,
  readDraft as bffReadDraft,
  readDslFileByName as bffReadDslFileByName,
  readPublishHistoryEntry as bffReadPublishHistoryEntry,
  reload as bffReload,
  replaceDefinitionTests as bffReplaceDefinitionTests,
  restorePublishHistory as bffRestorePublishHistory,
  resumeSchedule as bffResumeSchedule,
  runDefinitionTests as bffRunDefinitionTests,
  runDsl as bffRunDsl,
  saveDraft as bffSaveDraft,
  searchObjects as bffSearchObjects,
  writeDslFileByName as bffWriteDslFileByName,
  type BffRequestInit,
} from './generated/useBffApi'
import { extractApiError } from '../utils/extractApiError'

export function useDslApi() {
  const log = useClientLogger('dsl')

  async function getDefinitions() {
    log.debug('fetching definitions')
    try {
      const result = await bffListDefinitions()
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
    return bffSearchObjects({ query })
  }

  async function preview(
    name: string,
    body: unknown,
    metadata?: Record<string, unknown>,
    headers?: Record<string, string>,
  ) {
    log.info('preview request', { name })
    const init: BffRequestInit = { body: { body, metadata } }
    if (headers) init.headers = headers
    return bffPreviewDsl(name, init)
  }

  async function run(
    name: string,
    body: unknown,
    metadata?: Record<string, unknown>,
    headers?: Record<string, string>,
  ) {
    log.info('run request', { name })
    const init: BffRequestInit = { body: { body, metadata } }
    if (headers) init.headers = headers
    return bffRunDsl(name, init)
  }

  async function explain(
    name: string,
    body: unknown,
    metadata?: Record<string, unknown>,
    headers?: Record<string, string>,
  ) {
    log.info('explain request', { name })
    const init: BffRequestInit = { body: { body, metadata } }
    if (headers) init.headers = headers
    return bffExplainDsl(name, init)
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
      source?: string
    },
  ) {
    log.info('saveDraft request', { name })
    return bffSaveDraft(name, { body: payload })
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
    return bffPublishDraft(name, { body: payload })
  }

  async function deleteDraft(name: string) {
    log.info('deleteDraft request', { name })
    return bffDeleteDraft(name)
  }

  async function readDslFile(name: string) {
    log.info('readDslFile request', { name })
    const result = await bffReadDslFileByName(name)
    return (result as { content?: string }).content ?? ''
  }

  async function writeDslFile(name: string, content: string) {
    log.info('writeDslFile request', { name })
    return bffWriteDslFileByName(name, { body: { content } })
  }

  async function listHelpers() {
    log.info('listHelpers request')
    return bffListHelpers()
  }

  async function exportDefinitions(includeDrafts?: boolean) {
    log.info('exportDefinitions request', { includeDrafts })
    const query = includeDrafts ? { include: 'drafts' } : {}
    return bffExportDefinitions({ query })
  }

  async function importDefinitions(bundle: unknown, dryRun?: boolean) {
    log.info('importDefinitions request', { dryRun })
    const query = dryRun ? { dryRun: 'true' } : {}
    return bffImportDefinitions({ body: bundle, query })
  }

  async function listDrafts(): Promise<unknown[]> {
    log.info('listDrafts request')
    const result = await bffListDrafts()
    return unwrapList(result)
  }

  async function readDraft(name: string) {
    log.info('readDraft request', { name })
    return bffReadDraft(name)
  }

  async function listPublishHistory(name: string) {
    log.info('listPublishHistory request', { name })
    return bffListPublishHistory(name) as Promise<DefinitionHistoryEntry[]>
  }

  async function restorePublishHistory(name: string, timestamp: string) {
    log.info('restorePublishHistory request', { name, timestamp })
    return bffRestorePublishHistory(name, timestamp)
  }

  async function getHistoryEntry(name: string, timestamp: string) {
    log.info('getHistoryEntry request', { name, timestamp })
    return bffReadPublishHistoryEntry(name, timestamp) as Promise<DefinitionHistoryEntry>
  }

  async function getHistoryDiff(name: string, timestamp: string) {
    log.info('getHistoryDiff request', { name, timestamp })
    return bffDiffPublishHistoryEntry(name, timestamp) as Promise<HistoryDiffResponse>
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
      return (await bffListCompileDiagnostics({ query })) as DiagnosticsPage
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
      return (await bffListDomainEvents({ query })) as DomainEventPage
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
      // Not covered by docs/openapi.json yet — this endpoint predates the
      // springdoc webhooks spec, so the call stays hand-written here.
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
      const result = await bffListDefinitionTests(name)
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
      return await bffReplaceDefinitionTests(name, {
        // The backend expects the raw JSON array of cases; cast past the
        // generated client's unknown-body typing.
        body: cases,
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
      return (await bffRunDefinitionTests(name, { query })) as DefinitionTestRunReport
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
    return bffReload()
  }

  async function listSchedules() {
    log.info('listSchedules request')
    return bffListSchedules()
  }

  async function createSchedule(payload: Record<string, unknown>) {
    log.info('createSchedule request', { definition: payload.definition })
    return bffCreateSchedule({ body: payload })
  }

  async function deleteSchedule(definition: string) {
    log.info('deleteSchedule request', { definition })
    return bffDeleteSchedule(definition)
  }

  async function getProcessDiagram(
    name: string,
    format: 'mermaid' | 'plantuml' | 'bpmn' = 'mermaid',
  ) {
    log.info('process diagram request', { name, format })
    return bffGetProcessDiagram(name, { query: { format } })
  }

  async function pauseSchedule(definition: string, reason?: string) {
    log.info('pauseSchedule request', { definition })
    return bffPauseSchedule(definition, { body: reason ? { reason } : undefined })
  }

  async function resumeSchedule(definition: string, reason?: string) {
    log.info('resumeSchedule request', { definition })
    return bffResumeSchedule(definition, { body: reason ? { reason } : undefined })
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
