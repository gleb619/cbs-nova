package cbs.nova.starter.controller;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.AuditDto;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.persistence.DslAuditSearchResult;
import cbs.nova.starter.service.DslAuditService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Functional handler for the read side of the append-only audit log. Registered as a
 * {@code RouterFunction} bean by
 * {@code cbs.nova.starter.config.router.DslAuditRouterConfiguration}.
 */
@RequiredArgsConstructor
public class DslAuditHandler {

  private final DslAuditService service;

  public ServerResponse list(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", StarterConstants.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", StarterConstants.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);
    String action = request.param("action").filter(s -> !s.isBlank()).orElse(null);

    DslAuditSearchResult result = service.search(action, skip, pageSize);
    List<AuditDto> items = result.items().stream()
            .map(AuditDto::from)
            .toList();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new PageResponse<>(items, result.total(), skip, pageSize));
  }
}
