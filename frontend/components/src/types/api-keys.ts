/**
 * API key admin surface returned by `/api/v1/dsl/api-keys`.
 *
 * The backend never sends the hash or plaintext after creation; only public
 * metadata is surfaced in the list.
 */
export type ApiKey = {
  id: number
  label: string
  prefix: string
  createdAt: string
  revokedAt?: string | null
  lastUsedAt?: string | null
}

/** Response from creating a key — the plaintext is returned exactly once. */
export type CreatedApiKey = {
  id: number
  label: string
  prefix: string
  key: string
}

export type CreateApiKeyPayload = {
  label: string
}
