package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

/**
 * Request body for {@code POST /api/dsl/auth/keys}. Carries just the human-readable label; the
 * plaintext key is generated server-side and never echoed back after creation.
 */
public record CreateApiKeyRequest(
        @JsonInclude(JsonInclude.Include.NON_NULL) String label,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode raw) {
}
