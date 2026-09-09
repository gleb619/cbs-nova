package cbs.nova.starter.controller;

import cbs.nova.starter.model.CompileDiagnosticDto;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.persistence.CompileDiagnosticRecordRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Functional handler for the read side of the append-only compile diagnostic log. Registered as a
 * {@code RouterFunction} bean by
 * {@link cbs.nova.starter.config.router.DslDiagnosticsRouterConfiguration}.
 */
@RequiredArgsConstructor
public class DslDiagnosticsHandler {

  private final CompileDiagnosticRecordRepository repository;

  public ServerResponse list(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", Pagination.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", Pagination.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);
    String definition = request.param("definition").filter(s -> !s.isBlank()).orElse(null);

    var result = repository.search(definition, skip, pageSize);
    List<CompileDiagnosticDto> items = result.items().stream()
            .map(CompileDiagnosticDto::from)
            .toList();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new PageResponse<>(items, result.total(), skip, pageSize));
  }
}
