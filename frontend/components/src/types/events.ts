import type { PageResponseOf } from './api'

/**
 * A domain event from `GET /api/dsl/events` (`DslEventDto`).
 *
 * The OpenAPI spec declares no `DslEventDto` schema and leaves
 * `PageResponse.items` untyped, so this mirrors the DTO by hand; if the spec
 * grows a schema, alias it from `./api` instead.
 */
export interface DomainEvent {
  id: number
  eventType: string
  aggregateType: string
  aggregateId: string
  correlationId?: string
  schemaVersion: number
  createdAt: string
  payload?: unknown
}

/** `PageResponse` envelope specialised for the events list. */
export type DomainEventPage = PageResponseOf<DomainEvent>

export interface DomainEventQuery {
  type?: string
  aggregateType?: string
  aggregateId?: string
  correlationId?: string
  since?: string
  limit: number
  offset: number
}
