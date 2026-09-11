package cbs.nova.starter.model;

/**
 * Request body for {@code POST /api/dsl/auth/keys}. Carries just the human-readable label; the
 * plaintext key is generated server-side and never echoed back after creation.
 */
public record CreateApiKeyRequest(String label) {
}
