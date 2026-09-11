package cbs.nova.starter.model;

/**
 * Response body for a successful {@code POST /api/dsl/auth/keys}. The plaintext key is returned
 * exactly once; every other endpoint only exposes
 * {@link cbs.nova.starter.service.ApiKeyStore.ApiKeyView}.
 */
public record CreatedApiKeyResponse(long id, String label, String prefix, String key) {
}
