package cbs.nova.starter.controller;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.AuditChainVerification;
import cbs.nova.starter.model.AuditDto;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.persistence.DslAuditSearchResult;
import cbs.nova.starter.persistence.DslAuditStore;
import cbs.nova.starter.security.JsonRedactor;
import cbs.nova.starter.service.DslAuditService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Functional handler for the read side of the append-only audit log. Registered as a
 * {@code RouterFunction} bean by
 * {@code cbs.nova.starter.config.router.DslAuditRouterConfiguration}.
 */
@Slf4j
public class DslAuditHandler {

  private final DslAuditService service;
  private final DslAuditStore store;
  private final JsonRedactor redactor;

  public DslAuditHandler(DslAuditService service, DslAuditStore store, JsonRedactor redactor) {
    this.service = service;
    this.store = store;
    this.redactor = redactor;
  }

  public DslAuditHandler(DslAuditService service, JsonRedactor redactor) {
    this(service, null, redactor);
  }

  public ServerResponse list(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", StarterConstants.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", StarterConstants.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);
    String action = request.param("action").filter(s -> !s.isBlank()).orElse(null);

    DslAuditSearchResult result = service.search(action, skip, pageSize);
    List<AuditDto> items = result.items().stream()
            .map(audit -> AuditDto.from(audit, redactor))
            .toList();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new PageResponse<>(items, result.total(), skip, pageSize));
  }

  /**
   * T880: walks the tamper-evident {@code dsl_audit} hash chain and returns a verdict envelope. The
   * store dependency is only resolved here — the legacy single-arg constructor still works for unit
   * tests that exercise the list path only (see {@code DslAuditHandlerTest#setUp}).
   */
  public ServerResponse verify(ServerRequest request) {
    if (store == null) {
      log.warn("[DSL audit] verify endpoint called but DslAuditStore is not wired");
      return ServerResponse.status(503)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new ErrorBody("DslAuditStore is not available in this context"));
    }
    AuditChainVerification verdict = store.verifyChain();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(verdict);
  }

  /** Local envelope so the 503 path returns JSON; mirrors the framework {@code ErrorResponse}. */
  public record ErrorBody(String message) {
  }
}
