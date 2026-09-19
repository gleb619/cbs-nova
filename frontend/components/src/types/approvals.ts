/**
 * DSL publish change-request approval types (T568).
 *
 * The backend exposes `/api/dsl/change-requests*` (submit/list/approve/reject).
 * The endpoints are declared in docs/openapi.json; these hand-written types
 * mirror the `ChangeRequest` schema for ergonomic use in composables/pages.
 */

/** Lifecycle status of a change request. */
export type ChangeRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUPERSEDED'

/** A publish approval request for a DSL draft, from `/api/dsl/change-requests*`. */
export interface ChangeRequest {
  id: number
  definitionName: string
  draftContent: string
  requestedBy: string
  requestedAt: string
  status: ChangeRequestStatus
  approvedBy: string | null
  approvedAt: string | null
  comment: string | null
}

/** Request body for approve (comment optional) and reject (comment required). */
export interface ChangeRequestDecisionPayload {
  comment?: string
}
