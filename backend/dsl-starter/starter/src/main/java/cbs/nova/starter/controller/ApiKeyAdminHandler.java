package cbs.nova.starter.controller;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.exception.ApiKeyNotFoundException;
import cbs.nova.starter.model.CreateApiKeyRequest;
import cbs.nova.starter.model.CreatedApiKeyResponse;
import cbs.nova.starter.service.ApiKeyStore;
import cbs.nova.starter.service.ApiKeyStore.ApiKeyView;
import cbs.nova.starter.service.ApiKeyStore.CreatedKey;
import cbs.nova.starter.service.DslAuditService;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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
 *
 * <p>
 * Audit: {@code API_KEY_CREATE} and {@code API_KEY_REVOKE} mutations are appended to
 * {@code dsl_audit} via {@link DslAuditService} (T776). Audit details intentionally exclude the
 * plaintext, hash, and any other key material — only the public {@code id}, {@code label} and
 * visible {@code prefix} are recorded.
 */
@RequiredArgsConstructor
public class ApiKeyAdminHandler {

  static final String ACTION_API_KEY_CREATE = "API_KEY_CREATE";
  static final String ACTION_API_KEY_REVOKE = "API_KEY_REVOKE";

  private final ApiKeyStore store;
  private final ObjectProvider<DslAuditService> auditServiceProvider;

  public ServerResponse list(ServerRequest request) {
    List<ApiKeyView> keys = store.list();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(keys);
  }

  /**
   * Creates a new key. Body is a typed {@link CreateApiKeyRequest} with a single {@code label}
   * field; the plaintext key is returned exactly once in the response.
   *
   * <p>
   * Writes an {@link #ACTION_API_KEY_CREATE} row on success and on parse/validation failure. The
   * recorded {@code details} only ever carry {@code id}, {@code label} and {@code prefix} — never
   * the plaintext or its hash.
   */
  public ServerResponse create(ServerRequest request) throws ServletException, IOException {
    String label;
    try {
      label = extractLabel(request);
    } catch (IllegalArgumentException e) {
      audit(request, ACTION_API_KEY_CREATE, "-", StarterConstants.OUTCOME_FAILURE,
              Map.of("error", e.getMessage()));
      throw e;
    }
    CreatedKey created = store.create(label);
    audit(request, ACTION_API_KEY_CREATE, String.valueOf(created.id()),
            StarterConstants.OUTCOME_SUCCESS,
            Map.of("id", created.id(), "label", created.label(), "prefix", created.prefix()));
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
   *
   * <p>
   * Writes an {@link #ACTION_API_KEY_REVOKE} row on success and on the {@code Long.parseLong}
   * failure path. The audit row's {@code target} is the path-id verbatim; details are omitted so no
   * key material is recorded even on the failure branch.
   */
  public ServerResponse revoke(ServerRequest request) {
    long id;
    try {
      id = Long.parseLong(request.pathVariable("id"));
    } catch (NumberFormatException e) {
      audit(request, ACTION_API_KEY_REVOKE, request.pathVariable("id"),
              StarterConstants.OUTCOME_FAILURE, Map.of("error", "id must be a long"));
      throw new IllegalArgumentException("id must be a long", e);
    }
    boolean changed = store.revoke(id);
    if (!changed) {
      audit(request, ACTION_API_KEY_REVOKE, String.valueOf(id),
              StarterConstants.OUTCOME_FAILURE, Map.of("error", "not_found"));
      throw new ApiKeyNotFoundException(id);
    }
    audit(request, ACTION_API_KEY_REVOKE, String.valueOf(id),
            StarterConstants.OUTCOME_SUCCESS, Map.of("id", id));
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

  private void audit(ServerRequest request, String action, String target, String outcome,
          Object details) {
    if (auditServiceProvider == null) {
      return;
    }
    var auditService = auditServiceProvider.getIfAvailable();
    if (auditService == null) {
      return;
    }
    auditService.record(DslAuditService.currentActor(), action, target,
            DslAuditService.correlationIdOf(request), outcome, details);
  }
}
