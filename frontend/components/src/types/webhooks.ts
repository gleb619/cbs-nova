import type { PageResponseOf } from './api'

/**
 * A persisted webhook delivery outcome from `GET /api/dsl/webhooks/deliveries`
 * (`WebhookDeliveryRecord`).
 *
 * The OpenAPI spec declares no `WebhookDeliveryRecord` schema and leaves
 * `PageResponse.items` untyped, so this mirrors the record by hand; if the
 * spec grows a schema, alias it from `./api` instead.
 *
 * `status` is a plain string on purpose: the backend records both symbolic
 * values (`delivered`, `rejected`, `serialization_failed`, `failed`) and raw
 * HTTP status codes, and new values may appear without a spec change.
 */
export interface WebhookDeliveryRecord {
  id: number
  occurredAt: string
  subscriptionId: string
  eventType: string
  url: string
  status: string
  attempts: number
  lastError: string | null
  durationMs: number | null
}

/** `PageResponse` envelope specialised for the webhook deliveries list. */
export type WebhookDeliveryPage = PageResponseOf<WebhookDeliveryRecord>

export interface WebhookDeliveryQuery {
  subscriptionId?: string
  limit: number
  offset: number
}
