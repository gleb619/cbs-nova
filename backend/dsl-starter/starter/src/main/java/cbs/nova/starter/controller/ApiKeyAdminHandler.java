package cbs.nova.starter.controller;

import cbs.nova.starter.exception.ApiKeyNotFoundException;
import cbs.nova.starter.model.CreateApiKeyRequest;
import cbs.nova.starter.model.CreatedApiKeyResponse;
import cbs.nova.starter.service.ApiKeyStore;
import cbs.nova.starter.service.ApiKeyStore.ApiKeyView;
import cbs.nova.starter.service.ApiKeyStore.CreatedKey;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.JacksonException;

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

  public ServerResponse list(ServerRequest request) {
    List<ApiKeyView> keys = store.list();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(keys);
  }

  /**
   * Creates a new key. Body is a typed {@link CreateApiKeyRequest} with a single {@code label}
   * field; the plaintext key is returned exactly once in the response.
   */
  public ServerResponse create(ServerRequest request) throws ServletException, IOException {
    String label = extractLabel(request);
    CreatedKey created = store.create(label);
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreatedApiKeyResponse(
                    created.id(),
                    created.label(),
                    created.prefix(),
                    created.plaintext()));
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
   * Extracts the {@code label} from the request body. Malformed JSON is rejected with a clear
   * error; blank or missing labels are rejected before the store ever sees them.
   */
  private String extractLabel(ServerRequest request) throws IOException, ServletException {
    try {
      CreateApiKeyRequest body = request.body(CreateApiKeyRequest.class);
      if (body == null || body.label() == null || body.label().isBlank()) {
        throw new IllegalArgumentException("'label' is required and must not be blank");
      }
      return body.label();
    } catch (JacksonException e) {
      throw new IllegalArgumentException("malformed JSON body: " + e.getOriginalMessage(), e);
    }
  }
}
