import type { PageResponseOf } from './api'

/**
 * Notification rules engine types.
 *
 * The backend exposes `/api/dsl/notifications/*` (rules CRUD, channels,
 * fire log, test). These endpoints are not covered by docs/openapi.json yet,
 * so the types are hand-written here, mirroring the backend contract.
 */

/** Supported notification sink types. */
export type NotificationSinkType = 'webhook' | 'email' | 'slack' | 'pagerduty'

/** Event filter attached to a notification rule. */
export interface NotificationRuleEventFilter {
  eventType: string
  aggregateType?: string | null
  aggregateIdPattern?: string | null
  definitionPattern?: string | null
  status?: string | null
}

/** Delivery action attached to a notification rule. */
export interface NotificationRuleAction {
  sink: NotificationSinkType
  /** webhook / slack / pagerduty target URL. */
  url?: string | null
  /** webhook only; write-only — the backend never echoes it back. */
  secret?: string | null
  /** email recipient. */
  to?: string | null
}

/** A persisted notification rule from `/api/dsl/notifications/rules`. */
export interface NotificationRule {
  id: number
  name: string
  enabled: boolean
  eventFilter: NotificationRuleEventFilter
  actions: NotificationRuleAction[]
  priority: number
  rateClass: string
  createdAt: string
  updatedAt: string
}

export interface CreateNotificationRulePayload {
  name: string
  enabled?: boolean
  eventFilter: NotificationRuleEventFilter
  actions: NotificationRuleAction[]
  priority?: number
  rateClass?: string
}

export type UpdateNotificationRulePayload = CreateNotificationRulePayload

/** Channel availability from `GET /api/dsl/notifications/channels`. */
export interface NotificationChannel {
  type: NotificationSinkType
  enabled: boolean
  /** True when the channel has no real backend integration yet. */
  placeholder?: boolean
}

/** A recorded notification firing from `GET /api/dsl/notifications/fire-log`. */
export interface NotificationFiring {
  id: number
  eventId: number
  ruleId: number
  ruleName: string
  sink: string
  outcome: string
  detail?: string | null
  durationMs?: number | null
  createdAt: string
}

/** Paged envelope for `GET /api/dsl/notifications/rules`. */
export type NotificationRulePage = PageResponseOf<NotificationRule>

/** Paged envelope for `GET /api/dsl/notifications/fire-log`. */
export type NotificationFireLogPage = PageResponseOf<NotificationFiring>

export interface NotificationFireLogQuery {
  offset: number
  limit: number
  ruleId?: string
}

/** Request body for `POST /api/dsl/notifications/test`. */
export interface NotificationTestPayload {
  eventType: string
  processName?: string
  status?: string
  runId?: string
}

/** Per-rule outcome entry in the test response. */
export interface NotificationTestFireResult {
  ruleId: number
  ruleName: string
  sink: string
  outcome: string
  detail?: string | null
}

/** Response of `POST /api/dsl/notifications/test`. */
export interface NotificationTestResult {
  matchedRuleIds: number[]
  fireResults: NotificationTestFireResult[]
}
