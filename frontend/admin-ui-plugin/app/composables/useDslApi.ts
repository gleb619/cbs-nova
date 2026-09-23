import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import {
  type ChangeRequest,
  type CreateNotificationRulePayload,
  type DefinitionTestCase,
  type DefinitionTestRunReport,
  type DiagnosticsPage,
  type DomainEventPage,
  type DomainEventQuery,
  type HelperCatalogEntry,
  type NotificationChannel,
  type NotificationFireLogPage,
  type NotificationFireLogQuery,
  type NotificationRule,
  type NotificationRulePage,
  type NotificationTestPayload,
  type NotificationTestResult,
  type ObjectStructureDto,
  type PromoteResult,
  type PromotionDefinition,
  type PromotionEnvironment,
  type PromotionRequest,
  unwrapList,
  type WebhookDeliveryPage,
  type WebhookDeliveryQuery,
} from '@cbs/components'
import { $fetch } from 'ofetch'
import type { DefinitionHistoryEntry, HistoryDiffResponse } from '../components/DslHistoryPanel.vue'
import { extractApiError } from '../utils/extractApiError'
import {
  type BffRequestInit,
  approveChangeRequest as bffApproveChangeRequest,
  createSchedule as bffCreateSchedule,
  deleteDraft as bffDeleteDraft,
  deleteSchedule as bffDeleteSchedule,
  diffPublishHistoryEntry as bffDiffPublishHistoryEntry,
  explainDsl as bffExplainDsl,
  exportDefinitions as bffExportDefinitions,
  getWorkingSet as bffGetWorkingSet,
  importDefinitions as bffImportDefinitions,
  listChangeRequests as bffListChangeRequests,
  listCompileDiagnostics as bffListCompileDiagnostics,
  listDefinitionTests as bffListDefinitionTests,
  listDomainEvents as bffListDomainEvents,
  listDrafts as bffListDrafts,
  listPublishHistory as bffListPublishHistory,
  listSchedules as bffListSchedules,
  pauseSchedule as bffPauseSchedule,
  previewDsl as bffPreviewDsl,
  publishDraft as bffPublishDraft,
  readDraft as bffReadDraft,
  readDslFileByName as bffReadDslFileByName,
  readPublishHistoryEntry as bffReadPublishHistoryEntry,
  rejectChangeRequest as bffRejectChangeRequest,
  reload as bffReload,
  replaceDefinitionTests as bffReplaceDefinitionTests,
  restorePublishHistory as bffRestorePublishHistory,
  resumeSchedule as bffResumeSchedule,
  runDefinitionTests as bffRunDefinitionTests,
  runDsl as bffRunDsl,
  saveDraft as bffSaveDraft,
  searchObjects as bffSearchObjects,
  submitChangeRequest as bffSubmitChangeRequest,
  writeDslFileByName as bffWriteDslFileByName,
} from './generated/useBffApi'

export function useDslApi() {
  const log = useClientLogger('dsl')

  async function getDefinitions() {
    log.debug('fetching definitions')
    try {
      const result = await bffGetWorkingSet()
      const list = unwrapList(result)
      log.info('definitions loaded', { count: list.length })
      return result
    } catch (err) {
      log.error('failed to load definitions', { error: extractApiError(err).message })
      throw err
    }
  }

  async function searchObjects(
    params: { page?: number; size?: number; query?: string; mode?: string } = {},
  ) {
    const query: Record<string, string> = {}
    if (params.page !== undefined) query.page = String(params.page)
    if (params.size !== undefined) query.size = String(params.size)
    if (params.query?.trim()) query.query = params.query.trim()
    if (params.mode?.trim()) query.mode = params.mode.trim()

    log.debug('searching objects', { query })
    return bffSearchObjects({ query }) as Promise<{
      items: HelperCatalogEntry[]
      total: number
      offset: number
      limit: number
    }>
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

  async function listHelpers(
    params: { search?: string; mode?: string; limit?: number; offset?: number } = {},
  ) {
    const page =
      params.offset !== undefined && params.limit ? Math.floor(params.offset / params.limit) : 0
    const size = params.limit ?? 100
    log.info('listHelpers request', { ...params })
    return searchObjects({
      page,
      size,
      query: params.search,
      mode: params.mode ?? 'exact',
    })
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

  async function fetchNotificationRules(params?: {
    offset?: number
    limit?: number
  }): Promise<NotificationRulePage> {
    log.info('fetchNotificationRules request', { ...params })
    const query: Record<string, string> = {}
    if (params?.offset !== undefined) query.offset = String(params.offset)
    if (params?.limit !== undefined) query.limit = String(params.limit)
    try {
      // Not covered by docs/openapi.json yet — hand-written like the
      // webhook deliveries call above.
      return (await $fetch('/api/v1/dsl/notifications/rules', { query })) as NotificationRulePage
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to load notification rules', { error: message })
      throw new Error(message)
    }
  }

  async function createNotificationRule(
    payload: CreateNotificationRulePayload,
  ): Promise<NotificationRule> {
    log.info('createNotificationRule request', { name: payload.name })
    return (await $fetch('/api/v1/dsl/notifications/rules', {
      method: 'POST',
      body: payload,
    })) as NotificationRule
  }

  async function fetchNotificationRule(id: number | string): Promise<NotificationRule> {
    log.info('fetchNotificationRule request', { id })
    return (await $fetch(`/api/v1/dsl/notifications/rules/${id}`)) as NotificationRule
  }

  async function updateNotificationRule(
    id: number | string,
    payload: CreateNotificationRulePayload,
  ): Promise<NotificationRule> {
    log.info('updateNotificationRule request', { id })
    return (await $fetch(`/api/v1/dsl/notifications/rules/${id}`, {
      method: 'PUT',
      body: payload,
    })) as NotificationRule
  }

  async function deleteNotificationRule(id: number | string): Promise<unknown> {
    log.info('deleteNotificationRule request', { id })
    return await $fetch(`/api/v1/dsl/notifications/rules/${id}`, { method: 'DELETE' })
  }

  async function setNotificationRuleEnabled(
    id: number | string,
    enabled: boolean,
  ): Promise<NotificationRule> {
    log.info('setNotificationRuleEnabled request', { id, enabled })
    return (await $fetch(`/api/v1/dsl/notifications/rules/${id}/enabled`, {
      method: 'POST',
      body: { enabled },
    })) as NotificationRule
  }

  async function fetchNotificationChannels(): Promise<NotificationChannel[]> {
    log.info('fetchNotificationChannels request')
    const result = await $fetch('/api/v1/dsl/notifications/channels')
    return unwrapList<NotificationChannel>(result)
  }

  async function fetchNotificationFireLog(
    params: NotificationFireLogQuery,
  ): Promise<NotificationFireLogPage> {
    log.info('fetchNotificationFireLog request', { ...params })
    const query: Record<string, string> = {
      limit: String(params.limit),
      offset: String(params.offset),
    }
    if (params.ruleId?.trim()) query.ruleId = params.ruleId.trim()
    try {
      return (await $fetch('/api/v1/dsl/notifications/fire-log', {
        query,
      })) as NotificationFireLogPage
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to load notification fire log', { error: message })
      throw new Error(message)
    }
  }

  async function testNotificationRules(
    payload: NotificationTestPayload,
  ): Promise<NotificationTestResult> {
    log.info('testNotificationRules request', { eventType: payload.eventType })
    return (await $fetch('/api/v1/dsl/notifications/test', {
      method: 'POST',
      body: payload,
    })) as NotificationTestResult
  }

  async function fetchObjectStructure(name: string): Promise<ObjectStructureDto | null> {
    log.info('fetchObjectStructure request', { name })
    try {
      return (await $fetch(
        `/api/v1/dsl/structures/${encodeURIComponent(name)}`,
      )) as ObjectStructureDto
    } catch (err) {
      if (extractApiError(err).status === 404) return null
      const message = extractApiError(err).message
      log.error('failed to load object structure', { error: message })
      throw new Error(message)
    }
  }

  async function fetchChangeRequests(params?: {
    definitionName?: string
    status?: string
  }): Promise<ChangeRequest[]> {
    log.info('fetchChangeRequests request', { ...params })
    const query: Record<string, string> = {}
    if (params?.definitionName?.trim()) query.definitionName = params.definitionName.trim()
    if (params?.status?.trim()) query.status = params.status.trim()
    try {
      const result = await bffListChangeRequests({ query })
      return unwrapList<ChangeRequest>(result)
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to load change requests', { error: message })
      throw new Error(message)
    }
  }

  async function submitChangeRequest(name: string): Promise<ChangeRequest> {
    log.info('submitChangeRequest request', { name })
    return (await bffSubmitChangeRequest(name)) as ChangeRequest
  }

  async function approveChangeRequest(id: number, comment?: string): Promise<ChangeRequest> {
    log.info('approveChangeRequest request', { id })
    return (await bffApproveChangeRequest(String(id), {
      body: comment ? { comment } : {},
    })) as ChangeRequest
  }

  async function rejectChangeRequest(id: number, comment: string): Promise<ChangeRequest> {
    log.info('rejectChangeRequest request', { id })
    return (await bffRejectChangeRequest(String(id), { body: { comment } })) as ChangeRequest
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

  async function pauseSchedule(definition: string, reason?: string) {
    log.info('pauseSchedule request', { definition })
    return bffPauseSchedule(definition, { body: reason ? { reason } : undefined })
  }

  async function resumeSchedule(definition: string, reason?: string) {
    log.info('resumeSchedule request', { definition })
    return bffResumeSchedule(definition, { body: reason ? { reason } : undefined })
  }

  async function fetchPromotionEnvironments(): Promise<PromotionEnvironment[]> {
    log.info('fetchPromotionEnvironments request')
    // Not covered by docs/openapi.json yet — hand-written like the
    // webhook deliveries call above.
    return (await $fetch('/api/v1/dsl/promote/environments')) as PromotionEnvironment[]
  }

  async function fetchPromotionDefinitions(env: string): Promise<PromotionDefinition[]> {
    log.info('fetchPromotionDefinitions request', { env })
    return (await $fetch('/api/v1/dsl/promote/definitions', {
      query: { env },
    })) as PromotionDefinition[]
  }

  async function promoteDefinitions(
    payload: PromotionRequest,
    dryRun: boolean,
  ): Promise<PromoteResult> {
    log.info('promoteDefinitions request', { ...payload, dryRun })
    const query = dryRun ? { dryRun: 'true' } : undefined
    return (await $fetch('/api/v1/dsl/promote', {
      method: 'POST',
      body: payload,
      query,
    })) as PromoteResult
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
    fetchObjectStructure,
    fetchWebhookDeliveries,
    fetchNotificationRules,
    createNotificationRule,
    fetchNotificationRule,
    updateNotificationRule,
    deleteNotificationRule,
    setNotificationRuleEnabled,
    fetchNotificationChannels,
    fetchNotificationFireLog,
    testNotificationRules,
    fetchChangeRequests,
    submitChangeRequest,
    approveChangeRequest,
    rejectChangeRequest,
    validateConstruct,
    reload,
    listSchedules,
    createSchedule,
    deleteSchedule,
    pauseSchedule,
    resumeSchedule,
    fetchPromotionEnvironments,
    fetchPromotionDefinitions,
    promoteDefinitions,
  }
}
