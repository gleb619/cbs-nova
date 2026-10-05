import type { PageResponseOf } from './api'

/**
 * A persisted audit entry from `GET /api/dsl/audit` (`AuditDto`).
 *
 * The OpenAPI spec currently declares `PageResponse.items` as an empty
 * schema, so this mirrors the backend DTO by hand; if the spec grows an
 * `AuditDto` schema, alias it from `./api` instead.
 */
export interface AuditDto {
  id: number
  occurredAt: string
  actor: string
  action: string
  target: string
  correlationId?: string | null
  outcome: string
  detailsJson?: string | null
}

/** `PageResponse` envelope specialised for the audit trail. */
export type AuditPage = PageResponseOf<AuditDto>

export interface AuditQuery {
  action?: string
  limit: number
  offset: number
}
