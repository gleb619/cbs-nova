package cbs.nova.starter.controller;

import cbs.nova.starter.exception.ApiKeyNotFoundException;
import cbs.nova.starter.model.CreateApiKeyRequest;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.service.ApiKeyStore;
import cbs.nova.starter.service.ApiKeyStore.ApiKeyView;
import cbs.nova.starter.service.ApiKeyStore.CreatedKey;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Functional handler for the admin API-key surface (T410). Thin HTTP shell around
 * {@link ApiKeyStore}: extracts path/body, delegates, and maps the result.
 *
 * <p>
 * Security: the {@code X-Api-Key} header is enforced upstream by
 * {@link cbs.nova.starter.web.ApiKeyAuthFilter}, which means every endpoint in this class requires
 * a valid key already. The plaintext is returned exactly once (from
 * {@link ApiKeyStore#create(String)}) and never surfaces in any other response.
 */
@RequiredArgsConstructor
public class ApiKeyAdminHandler {

  private final ApiKeyStore store;
  private final ObjectMapper objectMapper;

  /** Lists every stored key (label + prefix + timestamps). NEVER the hash, NEVER the plaintext. */
  public ServerResponse list(ServerRequest request) {
    List<ApiKeyView> keys = store.list();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(keys);
  }

  /**
   * Creates a new key. Body is a JSON object with a single {@code label} field; the plaintext key
   * is returned exactly once in the response. Subsequent list calls never expose the plaintext.
   */
  public ServerResponse create(ServerRequest request) throws ServletException, IOException {
    String label = extractLabel(request);
    CreatedKey created = store.create(label);
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                    "id", created.id(),
                    "label", created.label(),
                    "prefix", created.prefix(),
                    "key", created.plaintext()));
  }

  /**
   * Revokes the key with the given id. Idempotent: a missing id returns 404 with the standard
   * envelope, an already-revoked id returns 204 (no-op). The 404 envelope keeps a generic message
   * so the endpoint cannot be used to probe key-id existence beyond what list() already exposes.
   */
  public ServerResponse revoke(ServerRequest request) {
    long id = Long.parseLong(request.pathVariable("id"));
    boolean changed = store.revoke(id);
    if (!changed) {
      throw new ApiKeyNotFoundException(id);
    }
    return ServerResponse.noContent().build();
  }

  /**
   * Extracts the {@code label} from the request body. Accepts either a typed
   * {@link CreateApiKeyRequest} or a generic JSON object so callers can post
   * {@code {"label":"..."}} without binding to the dedicated type.
   */
  private String extractLabel(ServerRequest request) throws IOException, ServletException {
    try {
      JsonNode body = request.body(JsonNode.class);
      if (body == null || body.isNull() || !body.isObject()) {
        throw new IllegalArgumentException(
                "request body must be a JSON object with a 'label' field");
      }
      JsonNode labelNode = body.get("label");
      if (labelNode == null || !labelNode.isString()) {
        throw new IllegalArgumentException("'label' field is required and must be a string");
      }
      String label = labelNode.asString();
      if (label.isBlank()) {
        throw new IllegalArgumentException("'label' must not be blank");
      }
      return label;
    } catch (JacksonException e) {
      throw new IllegalArgumentException("malformed JSON body: " + e.getOriginalMessage(), e);
    }
  }

  /** Compile-time guard: keeps the model reference live. */
  @SuppressWarnings("unused")
  private static void keepReference(ErrorResponse ref) {
    // The handler does not throw the envelope directly — it throws ApiKeyNotFoundException and
    // DslExceptionHandler maps that to ErrorResponse — but the model import is intentional so
    // a future refactor can return it inline.
    if (ref == null) {
      throw new IllegalStateException("unused");
    }
  }
}
